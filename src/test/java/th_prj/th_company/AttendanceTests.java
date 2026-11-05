package th_prj.th_company;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

import th_prj.th_company.attendance.Attendance;
import th_prj.th_company.attendance.AttendanceDay;
import th_prj.th_company.attendance.AttendanceMonth;
import th_prj.th_company.attendance.AttendanceRepository;
import th_prj.th_company.attendance.AttendanceService;
import th_prj.th_company.member.Member;
import th_prj.th_company.member.MemberRepository;
import th_prj.th_company.member.Permission;
import th_prj.th_company.member.Role;

@SpringBootTest
@AutoConfigureMockMvc
class AttendanceTests {

	/** 테스트에서 시각을 바꿀 수 있는 Clock */
	static class MutableClock extends Clock {
		private Instant now = Instant.now();

		void set(LocalDateTime at) {
			now = at.toInstant(ZoneOffset.ofHours(9));
		}

		@Override public ZoneId getZone() { return ZoneId.of("Asia/Seoul"); }
		@Override public Clock withZone(ZoneId zone) { return this; }
		@Override public Instant instant() { return now; }
	}

	@TestConfiguration
	static class ClockOverride {
		@Bean
		@Primary
		MutableClock testClock() {
			return new MutableClock();
		}
	}

	private static final LocalDate DAY = LocalDate.of(2031, 5, 14);

	@Autowired private MockMvc mvc;
	@Autowired private MutableClock clock;
	@Autowired private AttendanceRepository attendanceRepository;
	@Autowired private MemberRepository memberRepository;
	@Autowired private PasswordEncoder passwordEncoder;
	@Autowired private AttendanceService attendanceService;

	private String loginId;
	private Long memberId;

	@BeforeEach
	void setUp() {
		loginId = "att" + UUID.randomUUID().toString().substring(0, 6);
		memberId = memberRepository.save(new Member(loginId, passwordEncoder.encode("pw"), "근태", Role.USER)).getId();
	}

	private MockHttpSession loginAt(LocalDateTime at) throws Exception {
		clock.set(at);
		return (MockHttpSession) mvc.perform(formLogin("/login").user(loginId).password("pw"))
			.andReturn().getRequest().getSession();
	}

	private Attendance record(LocalDate workDate) {
		return attendanceRepository.findByMemberIdAndWorkDate(memberId, workDate).orElse(null);
	}

	@Test
	void workDayStartsAt6am() {
		assertThat(AttendanceService.workDateOf(DAY.atTime(5, 59))).isEqualTo(DAY.minusDays(1));
		assertThat(AttendanceService.workDateOf(DAY.atTime(6, 0))).isEqualTo(DAY);
		assertThat(AttendanceService.workDateOf(DAY.atTime(23, 59))).isEqualTo(DAY);
	}

	@Test
	void loginBefore6amDoesNotCheckIn_firstLoginAfter6amDoes_laterLoginsDoNotChangeIt() throws Exception {
		loginAt(DAY.atTime(5, 30));
		assertThat(record(DAY)).isNull();
		assertThat(record(DAY.minusDays(1))).isNull();

		loginAt(DAY.atTime(8, 52));
		assertThat(record(DAY).getCheckInAt()).isEqualTo(DAY.atTime(8, 52));

		MockHttpSession session = loginAt(DAY.atTime(13, 10));
		assertThat(record(DAY).getCheckInAt()).isEqualTo(DAY.atTime(8, 52));

		mvc.perform(get("/").session(session))
			.andExpect(content().string(containsString("08:52")));
	}

	@Test
	void checkOutRecordsTime_canBeRedone_andWorksAfterMidnightForPreviousDay() throws Exception {
		MockHttpSession session = loginAt(DAY.atTime(9, 0));

		clock.set(DAY.atTime(18, 5));
		mvc.perform(post("/attendance/check-out").session(session).with(csrf()))
			.andExpect(flash().attribute("attendanceMessage", "퇴근 시간을 기록했습니다."));
		assertThat(record(DAY).getCheckOutAt()).isEqualTo(DAY.atTime(18, 5));

		// 야근 후 다음 날 새벽 1시에 다시 퇴근 → 전날 근무의 퇴근 시간이 바뀜
		clock.set(DAY.plusDays(1).atTime(1, 20));
		mvc.perform(post("/attendance/check-out").session(session).with(csrf()));
		assertThat(record(DAY).getCheckOutAt()).isEqualTo(DAY.plusDays(1).atTime(1, 20));
		mvc.perform(get("/").session(session))
			.andExpect(content().string(containsString("익일 01:20")))
			.andExpect(content().string(containsString("16시간 20분")));
	}

	@Test
	void checkOutWithoutCheckInIsRejected() throws Exception {
		MockHttpSession session = loginAt(DAY.atTime(5, 0));
		mvc.perform(get("/").session(session))
			.andExpect(content().string(containsString("오전 6시 이후 첫 로그인 시 출근이 기록됩니다.")));
		mvc.perform(post("/attendance/check-out").session(session).with(csrf()))
			.andExpect(flash().attribute("attendanceError", containsString("출근 기록이 없어")));
	}

	@Test
	void myMonth_showsLateWorkingAndAbsentDays_andCheckOutReturnsToAttendance() throws Exception {
		MockHttpSession session = loginAt(DAY.atTime(9, 30));
		AttendanceMonth month = attendanceService.myMonth(loginId, YearMonth.from(DAY));
		AttendanceDay today = month.days().get(DAY.getDayOfMonth() - 1);
		assertThat(today.late()).isTrue();
		assertThat(today.state()).isEqualTo(AttendanceDay.State.WORKING);
		// 계정은 그 전에 만들어졌으므로 지난 평일은 결근, 앞으로의 날은 표시 없음
		assertThat(month.absentCount()).isPositive();
		assertThat(month.days().get(DAY.getDayOfMonth()).state()).isEqualTo(AttendanceDay.State.NONE);
		assertThat(month.next()).isNull();

		mvc.perform(get("/attendance").param("month", "2031-05").session(session))
			.andExpect(status().isOk())
			.andExpect(content().string(containsString("지각")))
			.andExpect(content().string(containsString("근무중")))
			.andExpect(content().string(containsString("결근")))
			// 앞으로의 날짜까지 그 달의 모든 날짜를 보여준다
			.andExpect(content().string(containsString(month.days().get(30).displayDate())))
			.andExpect(content().string(containsString("upcoming")))
			.andExpect(content().string(org.hamcrest.Matchers.not(containsString("전체 근태 현황"))));

		clock.set(DAY.atTime(17, 0));
		mvc.perform(post("/attendance/check-out").param("back", "attendance").session(session).with(csrf()))
			.andExpect(redirectedUrl("/attendance"));
		AttendanceDay done = attendanceService.myMonth(loginId, YearMonth.from(DAY)).days().get(DAY.getDayOfMonth() - 1);
		assertThat(done.early()).isTrue();
		assertThat(done.tags()).extracting(AttendanceDay.Tag::text).contains("지각", "조퇴");
	}

	@Test
	void attendanceManagerCorrectsRecords_withReason_andOthersCannot() throws Exception {
		String managerId = "attm" + UUID.randomUUID().toString().substring(0, 6);
		Member manager = new Member(managerId, passwordEncoder.encode("pw"), "근태담당", Role.USER);
		manager.grant(Permission.ATTENDANCE_MANAGER);
		memberRepository.save(manager);
		clock.set(DAY.atTime(10, 0));
		MockHttpSession ms = (MockHttpSession) mvc.perform(formLogin("/login").user(managerId).password("pw"))
			.andReturn().getRequest().getSession();
		LocalDate yesterday = DAY.minusDays(1);

		mvc.perform(get("/attendance/manage/edit").param("member", memberId.toString())
				.param("date", yesterday.toString()).session(ms))
			.andExpect(status().isOk())
			.andExpect(content().string(containsString("근태 정정")));

		// 새벽 2시 퇴근 → 다음 날 02:00 으로 저장, 정정한 사람과 사유가 남는다
		mvc.perform(post("/attendance/manage/edit").session(ms).with(csrf())
				.param("memberId", memberId.toString()).param("date", yesterday.toString())
				.param("checkIn", "08:50").param("checkOut", "02:00").param("note", "야근 후 퇴근 버튼 누락"))
			.andExpect(redirectedUrl("/attendance/manage/members/" + memberId + "?month=2031-05"));
		Attendance a = record(yesterday);
		assertThat(a.getCheckInAt()).isEqualTo(yesterday.atTime(8, 50));
		assertThat(a.getCheckOutAt()).isEqualTo(DAY.atTime(2, 0));
		assertThat(a.getCorrectedBy()).isEqualTo("근태담당");
		assertThat(a.getCorrectionNote()).isEqualTo("야근 후 퇴근 버튼 누락");

		// 잘못된 입력
		mvc.perform(post("/attendance/manage/edit").session(ms).with(csrf())
				.param("memberId", memberId.toString()).param("date", yesterday.toString())
				.param("checkIn", "08:50").param("checkOut", "07:00").param("note", "x"))
			.andExpect(content().string(containsString("퇴근 시각이 출근 시각보다 이릅니다")));
		mvc.perform(post("/attendance/manage/edit").session(ms).with(csrf())
				.param("memberId", memberId.toString()).param("date", yesterday.toString())
				.param("checkIn", "08:50").param("note", ""))
			.andExpect(content().string(containsString("정정 사유를 입력하세요")));
		mvc.perform(post("/attendance/manage/edit").session(ms).with(csrf())
				.param("memberId", memberId.toString()).param("date", DAY.plusDays(1).toString())
				.param("checkIn", "08:50").param("note", "미리"))
			.andExpect(content().string(containsString("앞으로의 날짜")));

		// 관리 화면과 월별 화면에 보인다
		mvc.perform(get("/attendance/manage").param("date", yesterday.toString()).session(ms))
			.andExpect(status().isOk());
		mvc.perform(get("/attendance/manage/members/{id}", memberId).param("month", "2031-05").session(ms))
			.andExpect(content().string(containsString("익일 02:00")))
			.andExpect(content().string(containsString("야근 후 퇴근 버튼 누락")));

		// 출근을 비우면 기록 삭제
		mvc.perform(post("/attendance/manage/edit").session(ms).with(csrf())
				.param("memberId", memberId.toString()).param("date", yesterday.toString()).param("note", "잘못 등록"))
			.andExpect(status().is3xxRedirection());
		assertThat(record(yesterday)).isNull();

		// 근태관리자가 아니면 못 한다
		MockHttpSession plain = loginAt(DAY.atTime(10, 5));
		mvc.perform(post("/attendance/manage/edit").session(plain).with(csrf())
				.param("memberId", memberId.toString()).param("date", yesterday.toString())
				.param("checkIn", "09:00").param("note", "몰래"))
			.andExpect(status().isForbidden());
	}
}
