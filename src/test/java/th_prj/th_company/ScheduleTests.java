package th_prj.th_company;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

import th_prj.th_company.common.BusinessException;
import th_prj.th_company.member.Member;
import th_prj.th_company.member.MemberRepository;
import th_prj.th_company.member.Role;
import th_prj.th_company.org.Department;
import th_prj.th_company.org.DepartmentRepository;
import th_prj.th_company.schedule.MonthView;
import th_prj.th_company.schedule.ScheduleForm;
import th_prj.th_company.schedule.ScheduleService;
import th_prj.th_company.schedule.ScheduleType;

@SpringBootTest
@AutoConfigureMockMvc
class ScheduleTests {

	/** 다른 테스트 데이터와 겹치지 않는 먼 미래 달 */
	private static final YearMonth MONTH = YearMonth.of(2031, 3);

	@Autowired private MockMvc mvc;
	@Autowired private ScheduleService scheduleService;
	@Autowired private MemberRepository memberRepository;
	@Autowired private DepartmentRepository departmentRepository;
	@Autowired private PasswordEncoder passwordEncoder;

	private String alice;
	private String bob;
	private String carol;
	private String noDept;

	@BeforeEach
	void setUp() {
		String s = UUID.randomUUID().toString().substring(0, 6);
		Department teamA = departmentRepository.save(new Department("일정A" + s, null, 0));
		Department teamB = departmentRepository.save(new Department("일정B" + s, null, 0));
		alice = member("alice" + s, teamA);
		bob = member("bob" + s, teamA);
		carol = member("carol" + s, teamB);
		noDept = member("nodept" + s, null);
	}

	private String member(String loginId, Department dept) {
		Member m = new Member(loginId, passwordEncoder.encode("pw"), loginId, Role.USER);
		m.updateProfile(loginId, null, null, null, dept, null, null, null);
		return memberRepository.save(m).getLoginId();
	}

	private ScheduleForm form(ScheduleType type, String title, LocalDate start, LocalDate end, boolean allDay) {
		ScheduleForm f = scheduleService.newForm(start);
		f.setType(type);
		f.setTitle(title);
		f.setEndDate(end);
		f.setAllDay(allDay);
		return f;
	}

	private List<String> titlesInMonth(String loginId) {
		return scheduleService.month(MONTH, loginId).weeks().stream()
			.flatMap(List::stream).flatMap(d -> d.items().stream())
			.map(MonthView.Item::title).distinct().toList();
	}

	@Test
	void visibilityByType() {
		LocalDate day = MONTH.atDay(10);
		scheduleService.save(form(ScheduleType.COMPANY, "창립기념일", day, day, true), "sysadmin", true);
		scheduleService.save(form(ScheduleType.DEPARTMENT, "A팀 회의", day, day, false), alice, false);
		scheduleService.save(form(ScheduleType.PERSONAL, "앨리스 병원", day, day, false), alice, false);

		assertThat(titlesInMonth(alice)).contains("창립기념일", "A팀 회의", "앨리스 병원");
		assertThat(titlesInMonth(bob)).contains("창립기념일", "A팀 회의").doesNotContain("앨리스 병원");
		assertThat(titlesInMonth(carol)).contains("창립기념일").doesNotContain("A팀 회의", "앨리스 병원");
	}

	@Test
	void permissionRules() {
		LocalDate day = MONTH.atDay(12);
		assertThatThrownBy(() -> scheduleService.save(form(ScheduleType.COMPANY, "x", day, day, true), alice, false))
			.isInstanceOf(BusinessException.class).hasMessageContaining("관리자만");
		assertThatThrownBy(() -> scheduleService.save(form(ScheduleType.DEPARTMENT, "x", day, day, true), noDept, false))
			.isInstanceOf(BusinessException.class);
		assertThat(scheduleService.allowedTypes(alice, false))
			.containsExactly(ScheduleType.DEPARTMENT, ScheduleType.PERSONAL);

		Long deptId = scheduleService.save(form(ScheduleType.DEPARTMENT, "수정권한", day, day, true), alice, false);
		assertThat(scheduleService.get(deptId, bob, false).canEdit()).isFalse();
		assertThatThrownBy(() -> scheduleService.delete(deptId, bob, false)).isInstanceOf(AccessDeniedException.class);
		assertThatThrownBy(() -> scheduleService.get(deptId, carol, false)).isInstanceOf(AccessDeniedException.class);
		scheduleService.delete(deptId, "sysadmin", true);

		ScheduleForm backwards = form(ScheduleType.PERSONAL, "거꾸로", day, day, false);
		backwards.setStartTime(LocalTime.of(15, 0));
		backwards.setEndTime(LocalTime.of(14, 0));
		assertThatThrownBy(() -> scheduleService.save(backwards, alice, false)).hasMessageContaining("종료가 시작보다");
	}

	@Test
	void multiDayScheduleAppearsOnEveryDay() {
		LocalDate start = MONTH.atDay(20);
		scheduleService.save(form(ScheduleType.PERSONAL, "출장", start, start.plusDays(2), true), alice, false);

		List<MonthView.Day> days = scheduleService.month(MONTH, alice).weeks().stream().flatMap(List::stream).toList();
		for (int i = 0; i < 4; i++) {
			LocalDate d = start.plusDays(i);
			MonthView.Day day = days.stream().filter(x -> x.date().equals(d)).findFirst().orElseThrow();
			boolean has = day.items().stream().anyMatch(it -> it.title().equals("출장"));
			assertThat(has).as(d.toString()).isEqualTo(i < 3);
		}
	}

	@Test
	void pagesRender() throws Exception {
		MockHttpSession session = (MockHttpSession) mvc.perform(formLogin("/login").user(alice).password("pw"))
			.andReturn().getRequest().getSession();
		mvc.perform(get("/schedule").param("ym", MONTH.toString()).session(session))
			.andExpect(status().isOk())
			.andExpect(content().string(containsString("2031년 3월")));
		mvc.perform(get("/schedule/new").param("date", MONTH.atDay(5).toString()).session(session))
			.andExpect(status().isOk())
			.andExpect(content().string(not("회사일정")));

		mvc.perform(post("/schedule").session(session).with(csrf())
				.param("type", "PERSONAL").param("title", "웹 등록").param("startDate", MONTH.atDay(5).toString())
				.param("endDate", MONTH.atDay(5).toString()).param("startTime", "09:00").param("endTime", "10:30"))
			.andExpect(status().is3xxRedirection());
		mvc.perform(get("/schedule").param("ym", MONTH.toString()).session(session))
			.andExpect(content().string(containsString("웹 등록")))
			.andExpect(content().string(containsString("09:00")));
		mvc.perform(get("/").session(session)).andExpect(status().isOk());
	}

	private static org.hamcrest.Matcher<String> not(String text) {
		return org.hamcrest.Matchers.not(containsString(text));
	}
}
