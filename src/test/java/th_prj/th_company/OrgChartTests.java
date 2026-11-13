package th_prj.th_company;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

import th_prj.th_company.member.Member;
import th_prj.th_company.member.MemberRepository;
import th_prj.th_company.member.Role;
import th_prj.th_company.org.Department;
import th_prj.th_company.org.DepartmentRepository;
import th_prj.th_company.org.HrCode;
import th_prj.th_company.org.HrCodeRepository;
import th_prj.th_company.org.HrCodeType;
import th_prj.th_company.org.OrgChart;
import th_prj.th_company.org.OrgChartService;

@SpringBootTest
@AutoConfigureMockMvc
class OrgChartTests {

	@Autowired private MockMvc mvc;
	@Autowired private MemberRepository memberRepository;
	@Autowired private DepartmentRepository departmentRepository;
	@Autowired private HrCodeRepository hrCodeRepository;
	@Autowired private PasswordEncoder passwordEncoder;
	@Autowired private OrgChartService orgChartService;

	private Member member(String loginId, String name, Department dept, HrCode grade, HrCode duty, boolean enabled) {
		Member m = new Member(loginId, passwordEncoder.encode("pw"), name, Role.USER);
		m.updateProfile(name, null, null, null, dept, grade, duty, null);
		m.changeAccess(Role.USER, enabled);
		return memberRepository.save(m);
	}

	@Test
	void 부서_직속만_직책_직급순으로_보여주고_사용중지자는_숨긴다() throws Exception {
		Department division = departmentRepository.save(new Department("조직도본부", null, 900));
		Department teamA = departmentRepository.save(new Department("조직도A팀", division, 10));
		Department closed = departmentRepository.save(new Department("폐지된팀", division, 20));
		closed.update("폐지된팀", division, 20, false);
		departmentRepository.save(closed);

		HrCode senior = hrCodeRepository.save(new HrCode(HrCodeType.GRADE, "조직도부장", 1));
		HrCode junior = hrCodeRepository.save(new HrCode(HrCodeType.GRADE, "조직도사원", 99));
		HrCode leader = hrCodeRepository.save(new HrCode(HrCodeType.DUTY, "조직도팀장", 1));

		member("org_head", "본부장님", division, senior, null, true);
		member("org_junior", "가사원", teamA, junior, null, true);
		member("org_leader", "하팀장", teamA, senior, leader, true);
		member("org_left", "퇴사자", teamA, junior, null, false);
		member("org_closed", "폐지팀원", closed, junior, null, true);

		// 본부 선택: 본부 직속 인원만, 하위 팀 인원은 제외
		OrgChart divisionChart = orgChartService.load(division.getId());
		assertThat(divisionChart.selected().name()).isEqualTo("조직도본부");
		assertThat(divisionChart.members()).extracting(OrgChart.Person::name).containsExactly("본부장님");
		assertThat(divisionChart.departments()).extracting(OrgChart.Dept::name).doesNotContain("폐지된팀");

		// 팀 선택: 직책 있는 팀장이 먼저, 사용 중지된 사용자는 제외
		OrgChart teamChart = orgChartService.load(teamA.getId());
		assertThat(teamChart.selected().memberCount()).isEqualTo(2);
		assertThat(teamChart.members()).extracting(OrgChart.Person::name).containsExactly("하팀장", "가사원");

		MockHttpSession session = (MockHttpSession) mvc.perform(formLogin("/login").user("org_junior").password("pw"))
			.andReturn().getRequest().getSession();
		mvc.perform(get("/org").param("dept", division.getId().toString()).session(session))
			.andExpect(status().isOk())
			.andExpect(content().string(containsString("본부장님")))
			.andExpect(content().string(not(containsString("하팀장"))))
			.andExpect(content().string(not(containsString("폐지된팀"))));
	}

	@Test
	void 사용자는_내_부서가_관리자는_최상위_부서가_열린다() throws Exception {
		Department top = departmentRepository.findAllByOrderBySortOrderAscNameAsc().stream()
			.filter(d -> d.getParent() == null).findFirst()
			.orElseGet(() -> departmentRepository.save(new Department("최상위", null, 0)));
		Department myTeam = departmentRepository.save(new Department("내소속팀", top, 999));
		HrCode leaderDuty = hrCodeRepository.save(new HrCode(HrCodeType.DUTY, "내팀장직책", 1));
		Member boss = member("org_boss", "가팀장", myTeam, null, leaderDuty, true);
		Member me = member("org_me", "내사용자", myTeam, null, null, true);

		// 일반 사용자: 본인 부서, 목록 맨 위(팀장)가 아니라 본인이 선택된 상태
		MockHttpSession user = (MockHttpSession) mvc.perform(formLogin("/login").user("org_me").password("pw"))
			.andReturn().getRequest().getSession();
		var result = mvc.perform(get("/org").session(user)).andExpect(status().isOk()).andReturn();
		OrgChart userChart = (OrgChart) result.getModelAndView().getModel().get("org");
		assertThat(userChart.selected().id()).isEqualTo(myTeam.getId());
		assertThat(userChart.members().get(0).id()).isEqualTo(boss.getId());
		assertThat(userChart.focusId()).isEqualTo(me.getId());
		String html = result.getResponse().getContentAsString();
		String myCard = html.substring(html.indexOf("data-card=\"" + me.getId() + "\""));
		assertThat(myCard.substring(0, myCard.indexOf(">"))).doesNotContain("hidden");
		String bossCard = html.substring(html.indexOf("data-card=\"" + boss.getId() + "\""));
		assertThat(bossCard.substring(0, bossCard.indexOf(">"))).contains("hidden");

		// 다른 부서를 고르면 그 부서 맨 위 사람
		OrgChart other = (OrgChart) mvc.perform(get("/org").param("dept", top.getId().toString()).session(user))
			.andReturn().getModelAndView().getModel().get("org");
		assertThat(other.focusId()).isEqualTo(other.members().isEmpty() ? null : other.members().get(0).id());

		MockHttpSession admin = (MockHttpSession) mvc.perform(formLogin("/login").user("sysadmin").password("a"))
			.andReturn().getRequest().getSession();
		Object adminChart = mvc.perform(get("/org").session(admin))
			.andExpect(status().isOk())
			.andReturn().getModelAndView().getModel().get("org");
		OrgChart chart = (OrgChart) adminChart;
		assertThat(chart.selected()).isEqualTo(chart.departments().get(0));
	}

	@Test
	void 없는_부서를_요청하면_첫_부서를_보여준다() {
		departmentRepository.save(new Department("기본선택부서", null, 0));
		OrgChart chart = orgChartService.load(-1L);
		assertThat(chart.selected()).isEqualTo(chart.departments().get(0));
	}
}
