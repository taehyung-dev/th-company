package th_prj.th_company;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;

import th_prj.th_company.member.DepartmentPermissionRepository;
import th_prj.th_company.member.Member;
import th_prj.th_company.member.MemberRepository;
import th_prj.th_company.member.Permission;
import th_prj.th_company.member.Role;
import th_prj.th_company.org.Department;
import th_prj.th_company.org.DepartmentRepository;

@SpringBootTest
@AutoConfigureMockMvc
class PermissionTests {

	@Autowired private MockMvc mvc;
	@Autowired private MemberRepository memberRepository;
	@Autowired private DepartmentRepository departmentRepository;
	@Autowired private DepartmentPermissionRepository departmentPermissionRepository;
	@Autowired private PasswordEncoder passwordEncoder;
	@Autowired private TransactionTemplate tx;

	private String suffix;
	private Department division;
	private Department team;
	private String headLoginId;   // 본부 소속
	private String staffLoginId;  // 본부 아래 팀 소속
	private Long staffId;

	@BeforeEach
	void setUp() {
		suffix = UUID.randomUUID().toString().substring(0, 6);
		division = departmentRepository.save(new Department("권한본부" + suffix, null, 900));
		team = departmentRepository.save(new Department("권한팀" + suffix, division, 10));
		headLoginId = member("head" + suffix, division).getLoginId();
		Member staff = member("staff" + suffix, team);
		staffLoginId = staff.getLoginId();
		staffId = staff.getId();
	}

	private Member member(String loginId, Department dept) {
		Member m = new Member(loginId, passwordEncoder.encode("pw"), loginId, Role.USER);
		m.updateProfile(loginId, null, null, null, dept, null, null, null);
		return memberRepository.save(m);
	}

	private MockHttpSession login(String id, String pw) throws Exception {
		return (MockHttpSession) mvc.perform(formLogin("/login").user(id).password(pw))
			.andReturn().getRequest().getSession();
	}

	private List<String> authorities(String loginId) throws Exception {
		SecurityContext ctx = (SecurityContext) login(loginId, "pw").getAttribute(
			HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY);
		return ctx.getAuthentication().getAuthorities().stream().map(GrantedAuthority::getAuthority).toList();
	}

	private Set<Permission> permissionsOf(Long id) {
		return tx.execute(s -> memberRepository.findById(id).orElseThrow().getPermissions());
	}

	@Test
	void 사용자에게_준_권한은_다음_로그인부터_적용되고_회수할_수_있다() throws Exception {
		MockHttpSession admin = login("sysadmin", "a");
		mvc.perform(get("/admin/permissions").session(admin))
			.andExpect(status().isOk())
			.andExpect(content().string(containsString("근태관리자")))
			.andExpect(content().string(containsString("일정관리자")));

		mvc.perform(post("/admin/permissions/board-manager/grant").session(admin).with(csrf())
				.param("memberIds", staffId.toString()))
			.andExpect(flash().attribute("message", containsString("사용자 1명")));
		assertThat(permissionsOf(staffId)).containsExactly(Permission.BOARD_MANAGER);
		assertThat(authorities(staffLoginId)).contains("ROLE_USER", "PERM_BOARD_MANAGER");

		// 이미 대상이면 건너뜀
		mvc.perform(post("/admin/permissions/board-manager/grant").session(admin).with(csrf())
				.param("memberIds", staffId.toString()))
			.andExpect(flash().attribute("error", containsString("추가할 대상이 없습니다")));

		mvc.perform(get("/admin/members").session(admin).param("q", staffLoginId))
			.andExpect(content().string(containsString("perm-tag")));

		mvc.perform(post("/admin/permissions/board-manager/revoke").session(admin).with(csrf())
				.param("memberId", staffId.toString()))
			.andExpect(flash().attribute("message", containsString("해제했습니다")));
		assertThat(authorities(staffLoginId)).doesNotContain("PERM_BOARD_MANAGER");
	}

	@Test
	void 하위부서_제외로_부서에_준_권한은_그_부서만_해당하고_하위포함으로_바꿀_수_있다() throws Exception {
		MockHttpSession admin = login("sysadmin", "a");
		mvc.perform(post("/admin/permissions/schedule-manager/grant").session(admin).with(csrf())
				.param("deptIds", division.getId().toString()))
			.andExpect(flash().attribute("message", containsString("부서 1개")));

		// 본부 소속은 권한 있음, 하위 팀 소속은 없음
		assertThat(authorities(headLoginId)).contains("PERM_SCHEDULE_MANAGER");
		assertThat(authorities(staffLoginId)).doesNotContain("PERM_SCHEDULE_MANAGER");

		// 하위 부서 포함으로 바꾸면 팀 소속도 권한
		mvc.perform(post("/admin/permissions/schedule-manager/dept-sub").session(admin).with(csrf())
				.param("deptId", division.getId().toString()).param("includeSub", "true"))
			.andExpect(flash().attribute("message", containsString("하위 부서를 포함")));
		assertThat(authorities(staffLoginId)).contains("PERM_SCHEDULE_MANAGER");

		mvc.perform(get("/admin/permissions").session(admin).param("p", "schedule-manager"))
			.andExpect(content().string(containsString("권한본부" + suffix)))
			.andExpect(content().string(containsString("sub-toggle on")));

		// 부서 해제
		mvc.perform(post("/admin/permissions/schedule-manager/revoke-dept").session(admin).with(csrf())
				.param("deptId", division.getId().toString()))
			.andExpect(flash().attribute("message", containsString("부서의 일정관리자 권한을 해제")));
		assertThat(authorities(headLoginId)).doesNotContain("PERM_SCHEDULE_MANAGER");
	}

	@Test
	void 하위포함_부서와_사용자에게_함께_주면_부서에_새로_온_사람도_받는다() throws Exception {
		MockHttpSession admin = login("sysadmin", "a");
		Member outsider = member("out" + suffix, null);
		mvc.perform(post("/admin/permissions/attendance-manager/grant").session(admin).with(csrf())
				.param("deptIds", division.getId().toString())
				.param("memberIds", outsider.getId().toString())
				.param("includeSub", "true"))
			.andExpect(flash().attribute("message", containsString("부서 1개(하위 부서 포함), 사용자 1명")));

		// 나중에 팀에 들어온 사람도 권한을 가짐
		String newcomer = member("new" + suffix, team).getLoginId();
		assertThat(authorities(newcomer)).contains("PERM_ATTENDANCE_MANAGER");
		assertThat(authorities(outsider.getLoginId())).contains("PERM_ATTENDANCE_MANAGER");

		// 부서를 삭제하면 부서 권한도 함께 사라짐 (먼저 인원과 하위 부서를 비움)
		tx.executeWithoutResult(s -> memberRepository.findAll().stream()
			.filter(m -> m.getDepartment() != null
				&& (m.getDepartment().getId().equals(team.getId()) || m.getDepartment().getId().equals(division.getId())))
			.forEach(m -> m.updateProfile(m.getName(), null, null, null, null, null, null, null)));
		mvc.perform(post("/admin/departments/{id}/delete", team.getId()).session(admin).with(csrf()));
		mvc.perform(post("/admin/departments/{id}/delete", division.getId()).session(admin).with(csrf()))
			.andExpect(flash().attribute("message", "부서를 삭제했습니다."));
		assertThat(departmentPermissionRepository.findByDepartmentIdAndPermission(division.getId(),
			Permission.ATTENDANCE_MANAGER)).isEmpty();
	}

	@Test
	void 관리자만_관리하고_잘못된_입력을_처리한다() throws Exception {
		MockHttpSession user = login(staffLoginId, "pw");
		mvc.perform(get("/admin/permissions").session(user)).andExpect(status().isForbidden());
		mvc.perform(post("/admin/permissions/board-manager/grant").session(user).with(csrf())
				.param("memberIds", staffId.toString()))
			.andExpect(status().isForbidden());

		MockHttpSession admin = login("sysadmin", "a");
		mvc.perform(post("/admin/permissions/unknown/grant").session(admin).with(csrf())
				.param("memberIds", staffId.toString()))
			.andExpect(status().isNotFound());
		mvc.perform(post("/admin/permissions/board-manager/grant").session(admin).with(csrf()))
			.andExpect(flash().attribute("error", "가운데 조직도에서 부서나 사용자를 체크하세요."));

		// 사용 중지된 사용자는 건너뜀
		tx.executeWithoutResult(s -> memberRepository.findById(staffId).orElseThrow().changeAccess(Role.USER, false));
		mvc.perform(post("/admin/permissions/attendance-manager/grant").session(admin).with(csrf())
				.param("memberIds", staffId.toString()))
			.andExpect(flash().attribute("error", containsString("사용 중지된 사용자")));
		assertThat(permissionsOf(staffId)).isEmpty();
	}
}
