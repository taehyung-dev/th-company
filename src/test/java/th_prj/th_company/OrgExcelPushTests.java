package th_prj.th_company;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import th_prj.th_company.common.XlsxWriter;
import th_prj.th_company.member.Member;
import th_prj.th_company.member.MemberRepository;
import th_prj.th_company.member.Permission;
import th_prj.th_company.member.Role;
import th_prj.th_company.notification.NotificationService;
import th_prj.th_company.org.Department;
import th_prj.th_company.org.DepartmentRepository;

/** 전체 근태 현황과 사내 주소록의 조직도 화면, 엑셀 내려받기, 실시간 알림 */
@SpringBootTest
@AutoConfigureMockMvc
class OrgExcelPushTests {

	@Autowired private MockMvc mvc;
	@Autowired private MemberRepository memberRepository;
	@Autowired private DepartmentRepository departmentRepository;
	@Autowired private PasswordEncoder passwordEncoder;
	@Autowired private NotificationService notificationService;

	private String suffix;
	private Department division;
	private Department team;
	private Member manager;
	private Member worker;

	@BeforeEach
	void setUp() {
		suffix = UUID.randomUUID().toString().substring(0, 6);
		division = departmentRepository.save(new Department("조직본부" + suffix, null, 950));
		team = departmentRepository.save(new Department("조직팀" + suffix, division, 10));
		manager = new Member("om" + suffix, passwordEncoder.encode("pw"), "근태담당" + suffix, Role.USER);
		manager.grant(Permission.ATTENDANCE_MANAGER);
		manager.updateProfile(manager.getName(), null, null, null, division, null, null, null);
		manager = memberRepository.save(manager);
		worker = new Member("ow" + suffix, passwordEncoder.encode("pw"), "팀원" + suffix, Role.USER);
		worker.updateProfile(worker.getName(), null, null, null, team, null, null, null);
		worker = memberRepository.save(worker);
	}

	private MockHttpSession login(Member m) throws Exception {
		return (MockHttpSession) mvc.perform(formLogin("/login").user(m.getLoginId()).password("pw"))
			.andReturn().getRequest().getSession();
	}

	@Test
	void 근태와_주소록은_부서트리와_고른_부서_인원을_보여준다() throws Exception {
		MockHttpSession s = login(manager);
		mvc.perform(get("/attendance/manage").session(s).param("dept", team.getId().toString()))
			.andExpect(status().isOk())
			.andExpect(content().string(containsString("org-tree")))
			.andExpect(content().string(containsString(worker.getName())));

		mvc.perform(get("/address/company").session(s).param("dept", team.getId().toString()))
			.andExpect(status().isOk())
			.andExpect(content().string(containsString("org-tree")))
			.andExpect(content().string(containsString(worker.getName())));
		// 검색하면 부서와 상관없이 전체에서 찾는다
		mvc.perform(get("/address/company").session(s).param("q", suffix))
			.andExpect(content().string(containsString("검색 결과")))
			.andExpect(content().string(containsString(worker.getName())))
			.andExpect(content().string(containsString(manager.getName())));
	}

	@Test
	void 엑셀_다운로드는_전원이_든_올바른_xlsx다() throws Exception {
		MockHttpSession s = login(manager);
		Map<String, String> attendance = unzip(mvc.perform(get("/attendance/manage/excel").session(s)
				.param("date", "2031-05-14"))
			.andExpect(status().isOk())
			.andExpect(header().string("Content-Type", XlsxWriter.CONTENT_TYPE))
			.andExpect(header().string("Content-Disposition", containsString(".xlsx")))
			.andReturn().getResponse());
		assertThat(attendance).containsKeys("[Content_Types].xml", "xl/workbook.xml", "xl/worksheets/sheet1.xml");
		assertThat(attendance.get("xl/worksheets/sheet1.xml")).contains(worker.getName(), manager.getName());

		Map<String, String> leave = unzip(mvc.perform(get("/attendance/manage/leave/excel").session(s)
				.param("year", "2031"))
			.andExpect(status().isOk())
			.andExpect(header().string("Content-Type", XlsxWriter.CONTENT_TYPE))
			.andReturn().getResponse());
		assertThat(leave.get("xl/workbook.xml")).contains("직원별 연차", "휴가 신청");
		assertThat(leave.get("xl/worksheets/sheet1.xml")).contains(worker.getName(), "조직팀" + suffix);

		// 근태관리자가 아니면 못 받는다
		mvc.perform(get("/attendance/manage/excel").session(login(worker))).andExpect(status().isForbidden());
	}

	@Test
	void 알림_스트림은_연결하면_안읽은_수를_보내고_저장_뒤_새_알림을_보낸다() throws Exception {
		MockHttpSession s = login(worker);
		MvcResult stream = mvc.perform(get("/notifications/stream").session(s))
			.andExpect(request().asyncStarted())
			.andReturn();
		MockHttpServletResponse response = stream.getResponse();
		assertThat(response.getContentAsString(StandardCharsets.UTF_8))
			.contains("event:count").contains("\"unread\":0");

		notificationService.send(worker, "전자결재", "실시간 확인" + suffix, "/approval");
		assertThat(response.getContentAsString(StandardCharsets.UTF_8))
			.contains("event:notification")
			.contains("실시간 확인" + suffix)
			.contains("\"unread\":1")
			.contains("/notifications/");

		// 다른 사람 알림은 오지 않는다
		notificationService.send(manager, "전자결재", "남의 알림" + suffix, "/approval");
		assertThat(response.getContentAsString(StandardCharsets.UTF_8)).doesNotContain("남의 알림" + suffix);
	}

	private static Map<String, String> unzip(MockHttpServletResponse response) throws Exception {
		Map<String, String> files = new HashMap<>();
		try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(response.getContentAsByteArray()),
			StandardCharsets.UTF_8)) {
			for (ZipEntry e; (e = zip.getNextEntry()) != null;) {
				files.put(e.getName(), new String(zip.readAllBytes(), StandardCharsets.UTF_8));
			}
		}
		return files;
	}
}
