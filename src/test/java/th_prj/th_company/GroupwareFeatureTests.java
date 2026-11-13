package th_prj.th_company;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.temporal.TemporalAdjusters;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

import th_prj.th_company.approval.ApprovalBox;
import th_prj.th_company.approval.ApprovalDocumentRepository;
import th_prj.th_company.approval.ApprovalLine;
import th_prj.th_company.approval.ApprovalLineRepository;
import th_prj.th_company.approval.ApprovalService;
import th_prj.th_company.approval.DocRow;
import th_prj.th_company.approval.DocStatus;
import th_prj.th_company.approval.DocView;
import th_prj.th_company.approval.DraftForm;
import th_prj.th_company.approval.LineStatus;
import th_prj.th_company.board.Board;
import th_prj.th_company.board.BoardRepository;
import th_prj.th_company.board.BoardType;
import th_prj.th_company.attendance.AttendanceDay;
import th_prj.th_company.attendance.AttendanceMonth;
import th_prj.th_company.attendance.AttendanceService;
import th_prj.th_company.file.FileAttachmentRepository;
import th_prj.th_company.file.FileOwner;
import th_prj.th_company.leave.LeaveRequest;
import th_prj.th_company.leave.LeaveRequestRepository;
import th_prj.th_company.leave.LeaveService;
import th_prj.th_company.leave.LeaveStatus;
import th_prj.th_company.member.Member;
import th_prj.th_company.member.MemberRepository;
import th_prj.th_company.member.Permission;
import th_prj.th_company.member.Role;
import th_prj.th_company.notification.NotificationService;
import th_prj.th_company.reservation.Reservation;
import th_prj.th_company.reservation.ReservationRepository;
import th_prj.th_company.reservation.RepeatRule;
import th_prj.th_company.reservation.Resource;
import th_prj.th_company.reservation.ResourceRepository;
import th_prj.th_company.reservation.ResourceType;

/**
 * 알림, 휴가, 공휴일, 첨부파일, 참조자, 대결, 전결, 반복 예약, 며칠 예약.
 */
@SpringBootTest
@AutoConfigureMockMvc
class GroupwareFeatureTests {

	@Autowired private MockMvc mvc;
	@Autowired private BoardRepository boardRepository;
	@Autowired private MemberRepository memberRepository;
	@Autowired private PasswordEncoder passwordEncoder;
	@Autowired private ApprovalService approvalService;
	@Autowired private ApprovalDocumentRepository documentRepository;
	@Autowired private ApprovalLineRepository lineRepository;
	@Autowired private AttendanceService attendanceService;
	@Autowired private LeaveService leaveService;
	@Autowired private LeaveRequestRepository leaveRepository;
	@Autowired private NotificationService notificationService;
	@Autowired private FileAttachmentRepository fileRepository;
	@Autowired private ResourceRepository resourceRepository;
	@Autowired private ReservationRepository reservationRepository;

	private Member user(String prefix, Permission... permissions) {
		String id = prefix + UUID.randomUUID().toString().substring(0, 6);
		Member m = new Member(id, passwordEncoder.encode("pw"), prefix, Role.USER);
		for (Permission p : permissions) {
			m.grant(p);
		}
		return memberRepository.save(m);
	}

	private MockHttpSession login(Member m) throws Exception {
		return (MockHttpSession) mvc.perform(formLogin("/login").user(m.getLoginId()).password("pw"))
			.andReturn().getRequest().getSession();
	}

	private Long draft(Member drafter, List<Member> approvers, List<Member> cc) {
		DraftForm form = new DraftForm();
		form.setTemplateId(approvalService.templates().get(0).getId());
		form.setTitle("문서");
		form.setContent("내용");
		form.setApproverIds(approvers.stream().map(Member::getId).toList());
		form.setCcIds(cc.stream().map(Member::getId).toList());
		return approvalService.submit(form, drafter.getLoginId());
	}

	private List<Long> box(ApprovalBox box, Member m) {
		return approvalService.box(box, m.getLoginId(), 0).map(DocRow::id).toList();
	}

	private List<ApprovalLine> lines(Long id) {
		return lineRepository.findByDocument(id);
	}

	private void delegate(Member from, Member to) throws Exception {
		LocalDate today = LocalDate.now();
		mvc.perform(post("/approval/delegation").session(login(from)).with(csrf())
				.param("delegateId", to.getId().toString())
				.param("startDate", today.toString()).param("endDate", today.plusDays(3).toString()))
			.andExpect(redirectedUrl("/approval/delegation"));
	}

	// ---------- 알림 ----------

	@Test
	void 종은_마이페이지_왼쪽에_있고_알림은_본인_것만_보며_읽음처리된다() throws Exception {
		Member drafter = user("nd");
		Member approver = user("na");
		MockHttpSession s = login(approver);
		String home = mvc.perform(get("/").session(s)).andReturn().getResponse().getContentAsString();
		assertThat(home.indexOf("aria-label=\"알림\"")).isPositive().isLessThan(home.indexOf("aria-label=\"마이페이지\""));
		assertThat(home).doesNotContain("class=\"noti-badge\"");

		Long doc = draft(drafter, List.of(approver), List.of());
		assertThat(notificationService.unreadCount(approver.getLoginId())).isEqualTo(1);
		mvc.perform(get("/").session(s)).andExpect(content().string(containsString("class=\"noti-badge\"")));
		mvc.perform(get("/notifications/recent").session(s))
			.andExpect(content().string(containsString("결재할 차례입니다")));

		Long id = notificationService.recent(approver.getLoginId()).getContent().get(0).id();
		// 남의 알림은 열 수 없다
		mvc.perform(get("/notifications/{id}/open", id).session(login(drafter))).andExpect(status().isNotFound());
		mvc.perform(get("/notifications/{id}/open", id).session(s)).andExpect(redirectedUrl("/approval/docs/" + doc));
		assertThat(notificationService.unreadCount(approver.getLoginId())).isZero();

		approvalService.approve(doc, approver.getLoginId(), null);
		assertThat(notificationService.recent(drafter.getLoginId()).getContent().get(0).message()).contains("승인되었습니다");
		mvc.perform(post("/notifications/read-all").session(login(drafter)).with(csrf()))
			.andExpect(redirectedUrl("/notifications"));
		assertThat(notificationService.unreadCount(drafter.getLoginId())).isZero();
	}

	// ---------- 공휴일, 휴가 ----------

	@Test
	void 공휴일은_미출근이_아니고_근태관리자만_관리한다() throws Exception {
		LocalDate day = LocalDate.of(2025, 3, 12);
		mvc.perform(get("/attendance/manage/holidays").session(login(user("hp")))).andExpect(status().isForbidden());

		Member member = user("hm");
		member.updateProfile("hm", null, null, null, null, null, null, LocalDate.of(2020, 1, 1));
		memberRepository.save(member);
		AttendanceMonth before = attendanceService.month(member.getId(), YearMonth.of(2025, 3));
		assertThat(on(before, day).state()).isEqualTo(AttendanceDay.State.ABSENT);

		MockHttpSession am = login(user("hma", Permission.ATTENDANCE_MANAGER));
		mvc.perform(post("/attendance/manage/holidays").session(am).with(csrf())
				.param("date", day.toString()).param("name", "창립기념일"))
			.andExpect(redirectedUrl("/attendance/manage/holidays?year=2025"));
		mvc.perform(post("/attendance/manage/holidays").session(am).with(csrf())
				.param("date", day.toString()).param("name", "두번째"))
			.andExpect(content().string(containsString("이미 등록된 날짜입니다")));
		mvc.perform(post("/attendance/manage/holidays/fixed").session(am).with(csrf()).param("year", "2025"))
			.andExpect(flash().attribute("message", "양력 공휴일 8건을 추가했습니다."));
		mvc.perform(get("/attendance/manage/holidays").param("year", "2025").session(am))
			.andExpect(content().string(containsString("창립기념일")))
			.andExpect(content().string(containsString("한글날")));

		AttendanceMonth after = attendanceService.month(member.getId(), YearMonth.of(2025, 3));
		assertThat(on(after, day).state()).isEqualTo(AttendanceDay.State.OFF);
		assertThat(on(after, day).tags()).extracting(AttendanceDay.Tag::text).containsExactly("창립기념일");
		assertThat(after.absentCount()).isEqualTo(before.absentCount() - 1);
	}

	private static AttendanceDay on(AttendanceMonth month, LocalDate date) {
		return month.days().stream().filter(d -> d.date().equals(date)).findFirst().orElseThrow();
	}

	private org.springframework.test.web.servlet.ResultActions requestLeave(MockHttpSession s, String type, LocalDate start,
			LocalDate end, Member approver) throws Exception {
		return mvc.perform(post("/attendance/leave").session(s).with(csrf())
			.param("type", type).param("startDate", start.toString()).param("endDate", end.toString())
			.param("approverIds", approver.getId().toString()));
	}

	@Test
	void 휴가는_전자결재를_거치고_주말을_빼며_근태에_반영된다() throws Exception {
		Member me = user("lv");
		me.updateProfile("lv", null, null, null, null, null, null, LocalDate.of(2020, 1, 1));
		memberRepository.save(me);
		Member boss = user("lb");
		MockHttpSession s = login(me);

		// 2025-04-04(금) ~ 04-07(월): 주말을 뺀 2일
		LocalDate fri = LocalDate.of(2025, 4, 4);
		requestLeave(s, "ANNUAL", fri, fri.plusDays(3), boss).andExpect(redirectedUrl("/attendance/leave?year=2025"));
		LeaveRequest r = leaveRepository.findByMember(me.getId(), fri, fri).get(0);
		assertThat(r.getDays()).isEqualTo(2);
		assertThat(r.getStatus()).isEqualTo(LeaveStatus.PENDING);
		assertThat(leaveService.summary(me.getLoginId(), 2025).pending()).isEqualTo(2);

		// 같은 기간 중복, 주말만, 결재선 없음은 거절
		requestLeave(s, "ANNUAL", fri, fri, boss).andExpect(content().string(containsString("이미 신청한 휴가")));
		requestLeave(s, "ANNUAL", fri.plusDays(1), fri.plusDays(2), boss)
			.andExpect(content().string(containsString("주말과 공휴일만")));
		mvc.perform(post("/attendance/leave").session(s).with(csrf()).param("type", "ANNUAL")
				.param("startDate", "2025-04-10").param("endDate", "2025-04-10"))
			.andExpect(content().string(containsString("결재자를 한 명 이상")));

		// 양식은 [결재하기]에 나오지 않고, 문서는 전자결재에서 회수할 수 없다
		assertThat(approvalService.templates()).noneMatch(t -> t.getName().equals("휴가신청서"));
		assertThat(approvalService.view(r.getDocumentId(), me.getLoginId()).canWithdraw()).isFalse();

		approvalService.approve(r.getDocumentId(), boss.getLoginId(), null);
		assertThat(leaveRepository.findById(r.getId()).orElseThrow().getStatus()).isEqualTo(LeaveStatus.APPROVED);
		assertThat(leaveService.summary(me.getLoginId(), 2025).used()).isEqualTo(2);

		AttendanceMonth month = attendanceService.month(me.getId(), YearMonth.of(2025, 4));
		assertThat(on(month, fri).state()).isEqualTo(AttendanceDay.State.LEAVE);
		assertThat(on(month, fri).tags()).extracting(AttendanceDay.Tag::text).containsExactly("연차");
		assertThat(on(month, fri.plusDays(1)).leave()).isNull();

		// 지난 휴가는 본인이 취소할 수 없고 근태관리자는 할 수 있다
		mvc.perform(post("/attendance/leave/{id}/cancel", r.getId()).session(s).with(csrf()))
			.andExpect(flash().attribute("error", "이미 시작한 휴가는 근태관리자에게 취소를 요청하세요."));
		mvc.perform(post("/attendance/manage/leave/{id}/cancel", r.getId()).param("year", "2025").session(s).with(csrf()))
			.andExpect(status().isForbidden());
		MockHttpSession am = login(user("lm", Permission.ATTENDANCE_MANAGER));
		mvc.perform(post("/attendance/manage/leave/{id}/cancel", r.getId()).param("year", "2025").session(am).with(csrf()))
			.andExpect(redirectedUrl("/attendance/manage/leave?year=2025"));
		assertThat(leaveService.summary(me.getLoginId(), 2025).used()).isZero();
	}

	@Test
	void 진행중_휴가를_취소하면_문서가_멈추고_반려되면_일수가_돌아오며_잔여일수를_넘을_수_없다() throws Exception {
		Member me = user("lc");
		Member boss = user("lcb");
		MockHttpSession s = login(me);
		LocalDate day = LocalDate.of(2031, 3, 3);

		requestLeave(s, "AM_HALF", day, day, boss).andExpect(status().is3xxRedirection());
		requestLeave(s, "PM_HALF", day, day, boss).andExpect(status().is3xxRedirection());
		List<LeaveRequest> halves = leaveRepository.findByMember(me.getId(), day, day);
		assertThat(halves).hasSize(2).allMatch(r -> r.getDays() == 0.5);

		mvc.perform(post("/attendance/leave/{id}/cancel", halves.get(0).getId()).session(s).with(csrf()))
			.andExpect(status().is3xxRedirection());
		assertThat(documentRepository.findById(halves.get(0).getDocumentId()).orElseThrow().getStatus())
			.isEqualTo(DocStatus.CANCELED);
		assertThat(box(ApprovalBox.PENDING, boss)).doesNotContain(halves.get(0).getDocumentId());

		approvalService.reject(halves.get(1).getDocumentId(), boss.getLoginId(), "안 됩니다");
		assertThat(leaveRepository.findById(halves.get(1).getId()).orElseThrow().getStatus()).isEqualTo(LeaveStatus.REJECTED);
		assertThat(leaveService.summary(me.getLoginId(), 2031).remaining()).isEqualTo(15);

		// 남은 연차보다 많이: 2031-03-10(월)부터 15일 평일이면 15일이 되므로 16일을 신청
		requestLeave(s, "ANNUAL", LocalDate.of(2031, 3, 10), LocalDate.of(2031, 3, 31), boss)
			.andExpect(content().string(containsString("남은 연차(15일)보다 많이")));
		// 병가는 차감하지 않으므로 된다
		requestLeave(s, "SICK", LocalDate.of(2031, 3, 10), LocalDate.of(2031, 3, 31), boss)
			.andExpect(status().is3xxRedirection());

		// 근태관리자가 일수를 정하면 그 값이 쓰이고, 비우면 돌아간다
		MockHttpSession am = login(user("lg", Permission.ATTENDANCE_MANAGER));
		mvc.perform(post("/attendance/manage/leave/grant").session(am).with(csrf())
				.param("member", me.getId().toString()).param("year", "2031").param("days", "20.5"))
			.andExpect(redirectedUrl("/attendance/manage/leave?year=2031"));
		assertThat(leaveService.summary(me.getLoginId(), 2031).granted()).isEqualTo(20.5);
		mvc.perform(post("/attendance/manage/leave/grant").session(am).with(csrf())
				.param("member", me.getId().toString()).param("year", "2031").param("days", ""))
			.andExpect(status().is3xxRedirection());
		assertThat(leaveService.summary(me.getLoginId(), 2031).granted()).isEqualTo(15);
		mvc.perform(get("/attendance/manage/leave").session(am).param("year", "2031")).andExpect(status().isOk());
		mvc.perform(get("/attendance/leave/new").session(s)).andExpect(status().isOk());
		mvc.perform(get("/my").session(s)).andExpect(content().string(containsString("남은 연차")));
	}

	@Test
	void 연차일수는_입사일을_따른다() {
		LocalDate today = LocalDate.of(2026, 7, 15);
		assertThat(LeaveService.autoDays(null, 2026, today)).isEqualTo(15);
		assertThat(LeaveService.autoDays(LocalDate.of(2026, 3, 1), 2026, today)).isEqualTo(4);
		assertThat(LeaveService.autoDays(LocalDate.of(2025, 1, 1), 2026, today)).isEqualTo(15);
		assertThat(LeaveService.autoDays(LocalDate.of(2023, 1, 1), 2026, today)).isEqualTo(16);
		assertThat(LeaveService.autoDays(LocalDate.of(1990, 1, 1), 2026, today)).isEqualTo(25);
	}

	// ---------- 첨부파일 ----------

	private static MockMultipartFile upload(String name) {
		return new MockMultipartFile("uploads", name, "text/plain", "hello".getBytes());
	}

	@Test
	void 결재_첨부파일은_문서를_볼_수_있는_사람만_받는다() throws Exception {
		Member me = user("fa");
		Member boss = user("fb");
		Member stranger = user("fs");
		MockHttpSession s = login(me);
		String location = mvc.perform(multipart("/approval/drafts").file(upload("견적서.txt")).session(s).with(csrf())
				.param("templateId", approvalService.templates().get(0).getId().toString())
				.param("action", "submit").param("title", "첨부").param("content", "본문")
				.param("approverIds", boss.getId().toString()))
			.andExpect(status().is3xxRedirection())
			.andReturn().getResponse().getRedirectedUrl();
		Long doc = Long.valueOf(location.substring(location.lastIndexOf('/') + 1));
		var files = approvalService.files(doc);
		assertThat(files).hasSize(1);
		Long file = files.get(0).id();

		mvc.perform(get(location).session(s)).andExpect(content().string(containsString("견적서.txt")));
		mvc.perform(get("/files/{id}", file).session(login(boss)))
			.andExpect(status().isOk()).andExpect(content().string("hello"));
		mvc.perform(get("/files/{id}", file).session(login(stranger))).andExpect(status().isForbidden());
	}

	@Test
	void 일정과_예약_첨부파일은_추가_삭제되고_원본과_함께_지워진다() throws Exception {
		Member me = user("fc");
		MockHttpSession s = login(me);
		LocalDate day = LocalDate.now().plusDays(20);

		mvc.perform(multipart("/schedule").file(upload("a.txt")).session(s).with(csrf())
				.param("type", "PERSONAL").param("title", "일정" + me.getLoginId()).param("allDay", "true")
				.param("startDate", day.toString()).param("endDate", day.toString()))
			.andExpect(status().is3xxRedirection());
		var f = fileRepository.findAll().stream().filter(x -> x.getOwnerType() == FileOwner.SCHEDULE).toList();
		var mine = f.get(f.size() - 1);
		mvc.perform(get("/schedule/{id}", mine.getOwnerId()).session(s)).andExpect(content().string(containsString("a.txt")));
		mvc.perform(get("/files/{id}", mine.getId()).session(login(user("fd")))).andExpect(status().isForbidden());
		mvc.perform(post("/schedule/{id}/delete", mine.getOwnerId()).session(s).with(csrf())).andExpect(status().is3xxRedirection());
		assertThat(fileRepository.findById(mine.getId())).isEmpty();

		Resource res = resourceRepository.save(new Resource(ResourceType.EQUIPMENT, "빔" + me.getLoginId(), null, null, null, 0));
		mvc.perform(multipart("/reservations").file(upload("b.txt")).file(upload("c.txt")).session(s).with(csrf())
				.param("kind", "RESOURCE").param("resourceId", res.getId().toString()).param("title", "첨부 예약")
				.param("date", day.toString()).param("startTime", "10:00").param("endTime", "11:00"))
			.andExpect(status().is3xxRedirection());
		Reservation r = reservationRepository.findConflicts(res.getId(), day.atStartOfDay(), day.plusDays(1).atStartOfDay(), 0L).get(0);
		var files = fileRepository.findByOwnerTypeAndOwnerIdOrderById(FileOwner.RESERVATION, r.getId());
		assertThat(files).hasSize(2);

		// 수정하면서 하나 지움
		mvc.perform(multipart("/reservations").session(s).with(csrf())
				.param("id", r.getId().toString()).param("kind", "RESOURCE").param("resourceId", res.getId().toString())
				.param("title", "첨부 예약").param("date", day.toString()).param("startTime", "10:00").param("endTime", "11:00")
				.param("deleteFileIds", files.get(0).getId().toString()))
			.andExpect(status().is3xxRedirection());
		assertThat(fileRepository.findByOwnerTypeAndOwnerIdOrderById(FileOwner.RESERVATION, r.getId())).hasSize(1);
		mvc.perform(post("/reservations/{id}/delete", r.getId()).session(s).with(csrf())).andExpect(status().is3xxRedirection());
		assertThat(fileRepository.findByOwnerTypeAndOwnerIdOrderById(FileOwner.RESERVATION, r.getId())).isEmpty();
	}

	// ---------- 참조자, 대결, 전결 ----------

	@Test
	void 참조자는_읽기만_하고_참조문서함에_나온다() throws Exception {
		Member me = user("cd");
		Member boss = user("cb");
		Member cc = user("cc");
		Long doc = draft(me, List.of(boss), List.of(cc));

		assertThat(box(ApprovalBox.CC, cc)).containsExactly(doc);
		assertThat(approvalService.counts(cc.getLoginId()).get("cc")).isEqualTo(1);
		assertThat(notificationService.recent(cc.getLoginId()).getContent().get(0).message()).contains("참조자로 지정");

		MockHttpSession s = login(cc);
		mvc.perform(get("/approval/docs/{id}", doc).session(s)).andExpect(status().isOk())
			.andExpect(content().string(not(containsString("decide-form"))));
		assertThat(approvalService.counts(cc.getLoginId()).get("cc")).isZero();
		mvc.perform(post("/approval/docs/{id}/approve", doc).session(s).with(csrf()))
			.andExpect(flash().attribute("error", "지금 결재할 차례가 아닙니다."));

		// 결재선의 사람은 참조자가 될 수 없다
		DraftForm bad = new DraftForm();
		bad.setTemplateId(approvalService.templates().get(0).getId());
		bad.setTitle("x");
		bad.setContent("x");
		bad.setApproverIds(List.of(boss.getId()));
		bad.setCcIds(List.of(boss.getId()));
		org.assertj.core.api.Assertions.assertThatThrownBy(() -> approvalService.submit(bad, me.getLoginId()))
			.hasMessageContaining("참조자로 지정할 수 없습니다");

		// 결재가 끝나면 참조자도 알림을 받는다
		approvalService.approve(doc, boss.getLoginId(), null);
		assertThat(notificationService.recent(cc.getLoginId()).getContent().get(0).message()).contains("승인되었습니다");
	}

	@Test
	void 대결자는_대신_결재하지만_전결과_본인_문서는_못_한다() throws Exception {
		Member drafter = user("dd");
		Member boss = user("db");
		Member next = user("dn");
		Member deputy = user("dp");
		delegate(boss, deputy);
		assertThat(notificationService.recent(deputy.getLoginId()).getContent().get(0).message()).contains("대결자로 지정");

		Long doc = draft(drafter, List.of(boss, next), List.of());
		assertThat(box(ApprovalBox.PENDING, deputy)).containsExactly(doc);
		assertThat(approvalService.counts(deputy.getLoginId()).get("pending")).isEqualTo(1);
		assertThat(notificationService.recent(deputy.getLoginId()).getContent().get(0).message()).contains("대신 결재");

		DocView view = approvalService.view(doc, deputy.getLoginId());
		assertThat(view.canDecide()).isTrue();
		assertThat(view.canFinalApprove()).isFalse();
		assertThat(view.deputyFor()).isEqualTo("db");

		MockHttpSession s = login(deputy);
		mvc.perform(post("/approval/docs/{id}/final-approve", doc).session(s).with(csrf()))
			.andExpect(flash().attribute("error", "대결자는 전결할 수 없습니다."));
		mvc.perform(post("/approval/docs/{id}/approve", doc).session(s).with(csrf()).param("comment", "대신"))
			.andExpect(flash().attribute("message", "승인했습니다."));

		assertThat(lines(doc).get(0).getStatus()).isEqualTo(LineStatus.APPROVED);
		assertThat(lines(doc).get(0).getActedBy().getId()).isEqualTo(deputy.getId());
		assertThat(lines(doc).get(1).getStatus()).isEqualTo(LineStatus.PENDING);
		assertThat(box(ApprovalBox.PENDING, deputy)).doesNotContain(doc);
		assertThat(box(ApprovalBox.PROGRESS, deputy)).contains(doc);
		mvc.perform(get("/approval/docs/{id}", doc).session(s))
			.andExpect(content().string(containsString("대결 dp")));

		// 대결자가 기안한 문서는 대결로 결재할 수 없다
		Long own = draft(deputy, List.of(boss), List.of());
		assertThat(box(ApprovalBox.PENDING, deputy)).doesNotContain(own);
		org.assertj.core.api.Assertions.assertThatThrownBy(() -> approvalService.approve(own, deputy.getLoginId(), null))
			.hasMessageContaining("차례가 아닙니다");

		// 해제하면 더 이상 대결할 수 없다
		Long later = draft(drafter, List.of(boss), List.of());
		var delegation = mvc.perform(get("/approval/delegation").session(login(boss)))
			.andExpect(content().string(containsString("적용 중"))).andReturn();
		assertThat(delegation.getResponse().getContentAsString()).contains("해제");
		Long id = ((List<th_prj.th_company.approval.DelegationService.Row>) delegation.getModelAndView().getModel().get("mine")).get(0).id();
		mvc.perform(post("/approval/delegation/{id}/delete", id).session(login(deputy)).with(csrf())).andExpect(status().isForbidden());
		mvc.perform(post("/approval/delegation/{id}/delete", id).session(login(boss)).with(csrf()))
			.andExpect(redirectedUrl("/approval/delegation"));
		assertThat(box(ApprovalBox.PENDING, deputy)).doesNotContain(later);
	}

	@Test
	void 대결_설정은_본인과_기간_겹침을_거부한다() throws Exception {
		Member boss = user("do");
		MockHttpSession s = login(boss);
		String today = LocalDate.now().toString();
		mvc.perform(post("/approval/delegation").session(s).with(csrf()).param("delegateId", boss.getId().toString())
				.param("startDate", today).param("endDate", today))
			.andExpect(content().string(containsString("본인을 대결자로")));
		delegate(boss, user("do2"));
		mvc.perform(post("/approval/delegation").session(s).with(csrf()).param("delegateId", user("do3").getId().toString())
				.param("startDate", today).param("endDate", today))
			.andExpect(content().string(containsString("같은 기간에 지정한 대결자")));
	}

	@Test
	void 전결하면_문서가_완결되고_뒤_단계는_건너뛴다() throws Exception {
		Member drafter = user("ed");
		Member first = user("e1");
		Member second = user("e2");
		Member third = user("e3");
		Long doc = draft(drafter, List.of(first, second, third), List.of());

		// 차례가 아닌 사람은 전결할 수 없다
		mvc.perform(post("/approval/docs/{id}/final-approve", doc).session(login(second)).with(csrf()))
			.andExpect(flash().attribute("error", "지금 결재할 차례가 아닙니다."));

		assertThat(approvalService.view(doc, first.getLoginId()).canFinalApprove()).isTrue();
		mvc.perform(get("/approval/docs/{id}", doc).session(login(first)))
			.andExpect(content().string(containsString("final-approve")));
		mvc.perform(post("/approval/docs/{id}/final-approve", doc).session(login(first)).with(csrf()).param("comment", "전결 처리"))
			.andExpect(flash().attribute("message", "전결했습니다."));

		assertThat(documentRepository.findById(doc).orElseThrow().getStatus()).isEqualTo(DocStatus.APPROVED);
		assertThat(lines(doc)).extracting(ApprovalLine::getStatus)
			.containsExactly(LineStatus.APPROVED, LineStatus.SKIPPED, LineStatus.SKIPPED);
		assertThat(lines(doc).get(0).isFinalApproval()).isTrue();
		assertThat(box(ApprovalBox.COMPLETED, third)).contains(doc);
		assertThat(notificationService.recent(third.getLoginId()).getContent().get(0).message()).contains("전결");
		mvc.perform(get("/approval/docs/{id}", doc).session(login(drafter)))
			.andExpect(content().string(containsString("전결로 생략")));

		// 마지막 결재자는 전결 대상이 아니다
		Long single = draft(drafter, List.of(first), List.of());
		assertThat(approvalService.view(single, first.getLoginId()).canFinalApprove()).isFalse();
	}

	// ---------- 반복 예약, 며칠 예약 ----------

	private Resource room(String name) {
		return resourceRepository.save(new Resource(ResourceType.ROOM, name + UUID.randomUUID().toString().substring(0, 6), null, 8, null, 0));
	}

	private org.springframework.test.web.servlet.ResultActions repeat(MockHttpSession s, Resource room, LocalDate first,
			String rule, LocalDate until, boolean skipConflicts) throws Exception {
		return mvc.perform(post("/reservations").session(s).with(csrf())
			.param("kind", "ROOM").param("resourceId", room.getId().toString()).param("title", "주간 회의")
			.param("date", first.toString()).param("startTime", "10:00").param("endTime", "11:00")
			.param("repeat", rule).param("repeatUntil", until == null ? "" : until.toString())
			.param("skipConflicts", String.valueOf(skipConflicts)));
	}

	private List<Reservation> reservations(Resource r, LocalDate from, LocalDate to) {
		return reservationRepository.findConflicts(r.getId(), from.atStartOfDay(), to.plusDays(1).atStartOfDay(), 0L);
	}

	@Test
	void 반복규칙_날짜_계산() {
		LocalDate jan31 = LocalDate.of(2031, 1, 31);
		assertThat(RepeatRule.MONTHLY.dates(jan31, LocalDate.of(2031, 4, 30)))
			.containsExactly(jan31, LocalDate.of(2031, 3, 31));
		LocalDate fri = LocalDate.of(2031, 1, 3);
		assertThat(RepeatRule.DAILY.dates(fri, fri.plusDays(3))).containsExactly(fri, fri.plusDays(3));
		assertThat(RepeatRule.BIWEEKLY.dates(fri, fri.plusDays(28))).hasSize(3);
	}

	@Test
	void 회의실_반복예약은_겹치면_알리거나_건너뛰고_이후_예약을_취소할_수_있다() throws Exception {
		Member me = user("rp");
		MockHttpSession s = login(me);
		Resource room = room("반복실");
		LocalDate first = LocalDate.now().plusDays(7).with(TemporalAdjusters.nextOrSame(DayOfWeek.MONDAY));
		LocalDate until = first.plusWeeks(3);

		// 둘째 주에 남의 예약이 있다
		mvc.perform(post("/reservations").session(login(user("ro"))).with(csrf())
				.param("kind", "ROOM").param("resourceId", room.getId().toString()).param("title", "선약")
				.param("date", first.plusWeeks(1).toString()).param("startTime", "10:30").param("endTime", "11:30"))
			.andExpect(status().is3xxRedirection());

		repeat(s, room, first, "WEEKLY", null, false).andExpect(content().string(containsString("반복 종료일을 입력")));
		repeat(s, room, first, "WEEKLY", first.plusYears(1).plusDays(1), false).andExpect(content().string(containsString("1년까지")));
		repeat(s, room, first, "WEEKLY", until, false).andExpect(content().string(containsString("이미 예약된 날이 1일")));
		assertThat(reservations(room, first, until)).hasSize(1);

		repeat(s, room, first, "WEEKLY", until, true)
			.andExpect(flash().attribute("message", "3건을 예약했습니다. 겹치는 1일은 건너뛰었습니다."));
		List<Reservation> mine = reservations(room, first, until).stream().filter(r -> r.getTitle().equals("주간 회의")).toList();
		assertThat(mine).hasSize(3);
		mvc.perform(get("/reservations/{id}", mine.get(0).getId()).session(s))
			.andExpect(content().string(containsString("매주 반복")))
			.andExpect(content().string(containsString("이후 전체 취소")));

		// 한 건만 취소하고, 그다음부터 전체 취소
		mvc.perform(post("/reservations/{id}/delete", mine.get(0).getId()).session(s).with(csrf())).andExpect(status().is3xxRedirection());
		assertThat(reservations(room, first, until)).hasSize(3);
		mvc.perform(post("/reservations/{id}/delete", mine.get(1).getId()).param("scope", "following").session(s).with(csrf()))
			.andExpect(flash().attribute("message", "반복 예약 2건을 취소했습니다."));
		assertThat(reservations(room, first, until)).extracting(Reservation::getTitle).containsExactly("선약");
	}

	@Test
	void 반복예약은_공휴일을_건너뛰고_자원은_반복할_수_없다() throws Exception {
		Member me = user("rh");
		MockHttpSession s = login(me);
		Resource room = room("휴일실");
		LocalDate first = LocalDate.of(2032, 3, 1).with(TemporalAdjusters.nextOrSame(DayOfWeek.MONDAY));
		mvc.perform(post("/attendance/manage/holidays").session(login(user("rha", Permission.ATTENDANCE_MANAGER))).with(csrf())
				.param("date", first.plusWeeks(1).toString()).param("name", "회사 휴일"))
			.andExpect(status().is3xxRedirection());
		repeat(s, room, first, "WEEKLY", first.plusWeeks(2), false)
			.andExpect(flash().attribute("message", "2건을 예약했습니다."));
		assertThat(reservations(room, first.plusWeeks(1), first.plusWeeks(1))).isEmpty();

		// 자원은 반복하지 않는다: 반복을 보내도 한 건만 예약된다
		Resource tool = resourceRepository.save(new Resource(ResourceType.EQUIPMENT, "장비" + me.getLoginId(), null, null, null, 0));
		mvc.perform(post("/reservations").session(s).with(csrf())
				.param("kind", "RESOURCE").param("resourceId", tool.getId().toString()).param("title", "대여")
				.param("date", first.toString()).param("startTime", "10:00").param("endTime", "11:00")
				.param("repeat", "WEEKLY").param("repeatUntil", first.plusWeeks(2).toString()))
			.andExpect(flash().attribute("message", "예약했습니다."));
		assertThat(reservations(tool, first, first.plusWeeks(2))).hasSize(1);
	}

	@Test
	void 자원은_여러_날에_걸쳐_예약되고_날을_넘어_겹침을_검사하며_회의실은_하루_안에서만_된다() throws Exception {
		Member me = user("md");
		MockHttpSession s = login(me);
		Resource car = resourceRepository.save(new Resource(ResourceType.VEHICLE, "차" + me.getLoginId(), null, 5, null, 0));
		LocalDate start = LocalDate.now().plusDays(30);
		LocalDate end = start.plusDays(4);

		mvc.perform(post("/reservations").session(s).with(csrf())
				.param("kind", "RESOURCE").param("resourceId", car.getId().toString()).param("title", "출장")
				.param("date", start.toString()).param("startTime", "09:00")
				.param("endDate", end.toString()).param("endTime", "18:00"))
			.andExpect(flash().attribute("message", "예약했습니다."));
		Reservation r = reservations(car, start, end).get(0);
		assertThat(r.getEndAt()).isEqualTo(LocalDateTime.of(end, java.time.LocalTime.of(18, 0)));

		// 가운데 날은 하루 전체가 막대로 보이고, 마지막 날까지 겹친다
		mvc.perform(get("/reservations/resources").param("date", start.plusDays(2).toString()).session(s))
			.andExpect(status().isOk()).andExpect(content().string(containsString("출장")));
		mvc.perform(get("/reservations/{id}", r.getId()).session(s)).andExpect(status().isOk());
		mvc.perform(get("/reservations/{id}/edit", r.getId()).session(s))
			.andExpect(content().string(containsString("value=\"" + end + "\"")));
		mvc.perform(post("/reservations").session(login(user("mo"))).with(csrf())
				.param("kind", "RESOURCE").param("resourceId", car.getId().toString()).param("title", "겹침")
				.param("date", end.toString()).param("startTime", "17:00").param("endTime", "19:00"))
			.andExpect(content().string(containsString("겹칩니다")));

		// 종료가 시작보다 빠름, 90일 초과
		mvc.perform(post("/reservations").session(s).with(csrf())
				.param("kind", "RESOURCE").param("resourceId", car.getId().toString()).param("title", "오류")
				.param("date", start.plusDays(60).toString()).param("startTime", "09:00")
				.param("endDate", start.plusDays(59).toString()).param("endTime", "18:00"))
			.andExpect(content().string(containsString("종료일이 시작일보다")));
		mvc.perform(post("/reservations").session(s).with(csrf())
				.param("kind", "RESOURCE").param("resourceId", car.getId().toString()).param("title", "오류")
				.param("date", start.plusDays(60).toString()).param("startTime", "09:00")
				.param("endDate", start.plusDays(160).toString()).param("endTime", "18:00"))
			.andExpect(content().string(containsString("90일까지")));

		// 회의실은 종료일을 보내도 하루 안이다
		Resource room = room("당일실");
		mvc.perform(post("/reservations").session(s).with(csrf())
				.param("kind", "ROOM").param("resourceId", room.getId().toString()).param("title", "회의")
				.param("date", start.toString()).param("startTime", "09:00")
				.param("endDate", end.toString()).param("endTime", "10:00"))
			.andExpect(status().is3xxRedirection());
		assertThat(reservations(room, start, end).get(0).getEndAt().toLocalDate()).isEqualTo(start);

		// 관리자가 남의 예약을 취소하면 예약자에게 알림
		mvc.perform(post("/reservations/{id}/delete", r.getId()).session(login(user("mm", Permission.RESOURCE_MANAGER))).with(csrf()))
			.andExpect(status().is3xxRedirection());
		assertThat(notificationService.recent(me.getLoginId()).getContent().get(0).message()).contains("취소했습니다");
	}

	@Test
	void 댓글은_글쓴이에게_알리지만_자기_댓글은_알리지_않는다() throws Exception {
		Member author = user("pa");
		Member reader = user("pr");
		MockHttpSession as = login(author);
		Board board = boardRepository.save(new Board(BoardType.BOARD, "알림게시판" + author.getLoginId(), null, 0));
		String location = mvc.perform(multipart("/boards/{b}/posts", board.getId()).param("title", "글").param("content", "x")
				.session(as).with(csrf()))
			.andReturn().getResponse().getRedirectedUrl();

		mvc.perform(post(location + "/comments").param("content", "내 댓글").session(as).with(csrf()))
			.andExpect(status().is3xxRedirection());
		assertThat(notificationService.unreadCount(author.getLoginId())).isZero();

		mvc.perform(post(location + "/comments").param("content", "좋아요").session(login(reader)).with(csrf()))
			.andExpect(status().is3xxRedirection());
		assertThat(notificationService.recent(author.getLoginId()).getContent().get(0).message()).contains("댓글을 남겼습니다");
	}

	@Test
	void 긴_알림_문구는_자른다() {
		Member m = user("nt");
		notificationService.send(m, "게시판", "가".repeat(400), "/");
		assertThat(notificationService.recent(m.getLoginId()).getContent().get(0).message()).hasSize(300).endsWith("…");
	}
}
