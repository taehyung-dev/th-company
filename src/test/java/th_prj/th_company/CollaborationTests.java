package th_prj.th_company;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
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

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

import th_prj.th_company.approval.ApprovalService;
import th_prj.th_company.approval.DocStatus;
import th_prj.th_company.approval.DocView;
import th_prj.th_company.approval.DraftForm;
import th_prj.th_company.common.BusinessException;
import th_prj.th_company.file.FileAttachmentService;
import th_prj.th_company.file.FileOwner;
import th_prj.th_company.member.Member;
import th_prj.th_company.member.MemberRepository;
import th_prj.th_company.member.Permission;
import th_prj.th_company.member.Role;
import th_prj.th_company.notification.NotificationService;
import th_prj.th_company.org.Department;
import th_prj.th_company.org.DepartmentRepository;
import th_prj.th_company.reservation.Reservation;
import th_prj.th_company.reservation.ReservationRepository;
import th_prj.th_company.reservation.Resource;
import th_prj.th_company.reservation.ResourceRepository;
import th_prj.th_company.reservation.ResourceType;
import th_prj.th_company.schedule.ScheduleForm;
import th_prj.th_company.schedule.ScheduleService;
import th_prj.th_company.schedule.ScheduleType;

/**
 * 결재 대기자 사용 중지 제한, 재기안, 일괄결재, 결재상태, 인쇄, 참석자, 일정 알림, 관리자 메뉴.
 */
@SpringBootTest
@AutoConfigureMockMvc
class CollaborationTests {

	private static final LocalDate DAY = LocalDate.now().plusYears(3);

	@Autowired private MockMvc mvc;
	@Autowired private ApprovalService approvalService;
	@Autowired private ScheduleService scheduleService;
	@Autowired private NotificationService notificationService;
	@Autowired private FileAttachmentService fileService;
	@Autowired private MemberRepository memberRepository;
	@Autowired private DepartmentRepository departmentRepository;
	@Autowired private ResourceRepository resourceRepository;
	@Autowired private ReservationRepository reservationRepository;
	@Autowired private PasswordEncoder passwordEncoder;

	private String suffix;
	private Department dept;
	private Member drafter;
	private Member first;
	private Member second;

	@BeforeEach
	void setUp() {
		suffix = UUID.randomUUID().toString().substring(0, 6);
		dept = departmentRepository.save(new Department("협업팀" + suffix, null, 0));
		drafter = member("drafter", dept);
		first = member("first", dept);
		second = member("second", dept);
	}

	private Member member(String prefix, Department department, Permission... permissions) {
		Member m = new Member(prefix + suffix, passwordEncoder.encode("pw"), prefix + "님", Role.USER);
		m.updateProfile(prefix + "님", null, null, null, department, null, null, null);
		for (Permission p : permissions) {
			m.grant(p);
		}
		return memberRepository.save(m);
	}

	private MockHttpSession login(String loginId, String password) throws Exception {
		return (MockHttpSession) mvc.perform(formLogin("/login").user(loginId).password(password))
			.andReturn().getRequest().getSession();
	}

	private MockHttpSession login(Member m) throws Exception {
		return login(m.getLoginId(), "pw");
	}

	private DraftForm form(String title, Long... approvers) {
		DraftForm form = approvalService.newDraft(approvalService.templates().get(0).getId());
		form.setTitle(title);
		form.setApproverIds(List.of(approvers));
		return form;
	}

	private List<String> messages(Member m) {
		return notificationService.list(m.getLoginId(), 0).map(NotificationService.Row::message).getContent();
	}

	// ---------- 1. 사용 중지 ----------

	private void disable(Member m, MockHttpSession admin, boolean expectBlocked) throws Exception {
		var result = mvc.perform(post("/admin/members/{id}", m.getId()).session(admin).with(csrf())
			.param("loginId", m.getLoginId()).param("name", m.getName()).param("role", "USER").param("enabled", "false"));
		if (expectBlocked) {
			result.andExpect(status().isOk()).andExpect(content().string(containsString("사용 중지할 수 없습니다")));
		} else {
			result.andExpect(status().is3xxRedirection());
		}
	}

	@Test
	void memberWithWaitingApprovalCannotBeDisabled() throws Exception {
		MockHttpSession admin = login("sysadmin", "a");
		Long id = approvalService.submit(form("대기 문서", first.getId(), second.getId()), drafter.getLoginId());

		// 지금 차례인 사람도, 뒤 단계에서 기다리는 사람도 막는다
		disable(first, admin, true);
		disable(second, admin, true);
		assertThat(memberRepository.findById(first.getId()).orElseThrow().isEnabled()).isTrue();

		approvalService.approve(id, first.getLoginId(), null);
		disable(first, admin, false);
		disable(second, admin, true);
		approvalService.approve(id, second.getLoginId(), null);
		disable(second, admin, false);
		assertThat(memberRepository.findById(second.getId()).orElseThrow().isEnabled()).isFalse();
	}

	@Test
	void withdrawnDocumentNoLongerBlocksDisabling() throws Exception {
		Long id = approvalService.submit(form("회수할 문서", first.getId()), drafter.getLoginId());
		approvalService.withdraw(id, drafter.getLoginId());
		disable(first, login("sysadmin", "a"), false);
	}

	// ---------- 2. 재기안 ----------

	@Test
	void redraftCopiesRejectedDocument() throws Exception {
		DraftForm original = form("반려 문서", first.getId(), second.getId());
		original.setCcIds(List.of(member("cc", dept).getId()));
		Long id = approvalService.submit(original, drafter.getLoginId(),
			List.of(new MockMultipartFile("uploads", "견적서.txt", "text/plain", "견적".getBytes())));

		assertThatThrownBy(() -> approvalService.redraft(id, drafter.getLoginId()))
			.isInstanceOf(BusinessException.class).hasMessageContaining("반려된 문서만");
		approvalService.reject(id, first.getLoginId(), "다시 작성");
		assertThatThrownBy(() -> approvalService.redraft(id, first.getLoginId()))
			.isInstanceOf(AccessDeniedException.class);
		assertThat(approvalService.view(id, drafter.getLoginId()).canRedraft()).isTrue();

		Long copy = approvalService.redraft(id, drafter.getLoginId());
		assertThat(copy).isNotEqualTo(id);
		assertThat(approvalService.redraft(id, drafter.getLoginId())).as("보관함에 있으면 그 문서로").isEqualTo(copy);

		DraftForm draft = approvalService.getDraft(copy, drafter.getLoginId());
		assertThat(draft.getTitle()).isEqualTo("반려 문서");
		assertThat(draft.getApproverIds()).containsExactly(first.getId(), second.getId());
		assertThat(draft.getCcIds()).hasSize(1);
		assertThat(fileService.list(FileOwner.APPROVAL, copy)).extracting("name").containsExactly("견적서.txt");

		// 고쳐서 상신하면 새 번호가 붙고, 원래 문서는 그대로 남는다
		draft.setTitle("고친 문서");
		approvalService.submit(draft, drafter.getLoginId());
		DocView view = approvalService.view(copy, drafter.getLoginId());
		DocView origin = approvalService.view(id, drafter.getLoginId());
		assertThat(view.status()).isEqualTo(DocStatus.IN_PROGRESS);
		assertThat(view.docNo()).isNotEqualTo(origin.docNo());
		assertThat(view.originDocNo()).isEqualTo(origin.docNo());
		assertThat(view.originId()).isEqualTo(id);
		assertThat(approvalService.view(copy, first.getLoginId()).originId()).as("원문서 권한이 없을 수 있어 번호만").isNull();
		assertThat(origin.status()).isEqualTo(DocStatus.REJECTED);
		assertThat(origin.title()).isEqualTo("반려 문서");
	}

	@Test
	void redraftDropsDisabledApprover_andWorksThroughWeb() throws Exception {
		Long id = approvalService.submit(form("결재자 퇴사", first.getId(), second.getId()), drafter.getLoginId());
		approvalService.reject(id, first.getLoginId(), "다시");
		disable(second, login("sysadmin", "a"), false);

		MockHttpSession session = login(drafter);
		mvc.perform(get("/approval/docs/{id}", id).session(session))
			.andExpect(content().string(containsString("재기안")));
		String location = mvc.perform(post("/approval/docs/{id}/redraft", id).session(session).with(csrf()))
			.andExpect(status().is3xxRedirection())
			.andReturn().getResponse().getRedirectedUrl();
		assertThat(location).endsWith("/edit");
		mvc.perform(get(location).session(session)).andExpect(status().isOk());

		Long copy = Long.valueOf(location.replaceAll("\\D+", ""));
		assertThat(approvalService.getDraft(copy, drafter.getLoginId()).getApproverIds()).containsExactly(first.getId());
	}

	// ---------- 3. 일괄결재 ----------

	@Test
	void bulkApproveHandlesEachDocumentSeparately() throws Exception {
		Long a = approvalService.submit(form("일괄 1", first.getId()), drafter.getLoginId());
		Long b = approvalService.submit(form("일괄 2", first.getId(), second.getId()), drafter.getLoginId());
		Long notMine = approvalService.submit(form("남의 문서", second.getId()), drafter.getLoginId());

		MockHttpSession session = login(first);
		mvc.perform(get("/approval/box/pending").session(session))
			.andExpect(content().string(containsString("일괄결재")))
			.andExpect(content().string(containsString("결재상태 보기")));
		mvc.perform(post("/approval/bulk-approve").session(session).with(csrf())
				.param("ids", a.toString(), b.toString(), notMine.toString()).param("comment", "일괄 승인"))
			.andExpect(redirectedUrl("/approval/box/pending"))
			.andExpect(flash().attribute("message", "2건을 승인했습니다."))
			.andExpect(flash().attribute("error", "1건은 결재할 수 없어 건너뛰었습니다."));

		assertThat(approvalService.view(a, drafter.getLoginId()).status()).isEqualTo(DocStatus.APPROVED);
		assertThat(approvalService.view(a, drafter.getLoginId()).lines().get(0).comment()).isEqualTo("일괄 승인");
		assertThat(approvalService.view(b, drafter.getLoginId()).status()).isEqualTo(DocStatus.IN_PROGRESS);
		assertThat(approvalService.view(notMine, drafter.getLoginId()).status()).isEqualTo(DocStatus.IN_PROGRESS);

		mvc.perform(post("/approval/bulk-approve").session(session).with(csrf()))
			.andExpect(flash().attribute("error", "결재할 문서를 선택하세요."));
	}

	@Test
	void bulkButtonOnlyInPendingBox() throws Exception {
		approvalService.submit(form("목록 문서", first.getId()), drafter.getLoginId());
		mvc.perform(get("/approval/box/progress").session(login(drafter)))
			.andExpect(content().string(containsString("결재상태 보기")))
			.andExpect(content().string(not(containsString("bulkButton"))));
	}

	// ---------- 4. 결재상태 ----------

	@Test
	void statusPanelShowsLines_andChecksPermission() throws Exception {
		Long id = approvalService.submit(form("상태 볼 문서", first.getId(), second.getId()), drafter.getLoginId());
		approvalService.approve(id, first.getLoginId(), "확인");

		String html = mvc.perform(get("/approval/status").session(login(drafter)).param("ids", id.toString()))
			.andExpect(status().isOk())
			.andReturn().getResponse().getContentAsString();
		assertThat(html).contains("상태 볼 문서", first.getName(), second.getName(), "승인", "결재중", "확인").doesNotContain("<html");
		assertThat(html.indexOf(second.getName())).as("높은 사람이 위로").isLessThan(html.indexOf(first.getName()));

		mvc.perform(get("/approval/status").session(login(member("outsider", dept))).param("ids", id.toString()))
			.andExpect(status().isForbidden());
		mvc.perform(get("/approval/status").session(login(drafter))).andExpect(status().isBadRequest());
	}

	// ---------- 5. 인쇄 ----------

	@Test
	void documentHasPrintButton() throws Exception {
		Long id = approvalService.submit(form("인쇄 문서", first.getId()), drafter.getLoginId());
		mvc.perform(get("/approval/docs/{id}", id).session(login(drafter)))
			.andExpect(content().string(containsString("window.print()")));
	}

	// ---------- 6. 회의실 참석자 ----------

	private Resource room() {
		return resourceRepository.save(new Resource(ResourceType.ROOM, "참석실" + suffix, null, 8, null, 0));
	}

	@Test
	void attendeesAreNotified_andSeeReservation() throws Exception {
		Resource room = room();
		MockHttpSession session = login(drafter);
		mvc.perform(get("/reservations/rooms/new").session(session))
			.andExpect(content().string(containsString("참석자 지정")));
		mvc.perform(post("/reservations").session(session).with(csrf()).param("kind", "ROOM")
				.param("resourceId", room.getId().toString()).param("date", DAY.toString())
				.param("startTime", "10:00").param("endTime", "11:00").param("title", "기획 회의")
				.param("attendeeIds", first.getId().toString(), drafter.getId().toString()))
			.andExpect(status().is3xxRedirection());

		Reservation saved = reservationRepository.findAll().stream()
			.filter(r -> r.getResource().getId().equals(room.getId())).findFirst().orElseThrow();
		assertThat(messages(first)).anyMatch(m -> m.contains("기획 회의") && m.contains("참석자로 지정"));
		assertThat(messages(drafter)).as("예약자에게는 보내지 않는다").noneMatch(m -> m.contains("기획 회의"));

		MockHttpSession attendee = login(first);
		mvc.perform(get("/reservations/rooms/my").session(attendee))
			.andExpect(content().string(containsString("기획 회의")))
			.andExpect(content().string(containsString("참석 · " + drafter.getName())));
		mvc.perform(get("/reservations/{id}", saved.getId()).session(attendee))
			.andExpect(content().string(containsString(first.getName())));
		mvc.perform(post("/reservations/{id}/delete", saved.getId()).session(attendee).with(csrf()))
			.andExpect(status().isForbidden());

		// 시간을 바꾸면 남은 참석자에게, 새로 넣은 사람에게, 빠진 사람에게 각각 알린다
		mvc.perform(post("/reservations").session(session).with(csrf()).param("id", saved.getId().toString()).param("kind", "ROOM")
				.param("resourceId", room.getId().toString()).param("date", DAY.toString())
				.param("startTime", "13:00").param("endTime", "14:00").param("title", "기획 회의")
				.param("attendeeIds", first.getId().toString()))
			.andExpect(status().is3xxRedirection());
		assertThat(messages(first)).anyMatch(m -> m.contains("시간 또는 장소를 변경"));
		mvc.perform(post("/reservations").session(session).with(csrf()).param("id", saved.getId().toString()).param("kind", "ROOM")
				.param("resourceId", room.getId().toString()).param("date", DAY.toString())
				.param("startTime", "13:00").param("endTime", "14:00").param("title", "기획 회의")
				.param("attendeeIds", second.getId().toString()))
			.andExpect(status().is3xxRedirection());
		assertThat(messages(first)).anyMatch(m -> m.contains("참석자에서 제외"));
		assertThat(messages(second)).anyMatch(m -> m.contains("참석자로 지정"));

		mvc.perform(post("/reservations/{id}/delete", saved.getId()).session(session).with(csrf()))
			.andExpect(status().is3xxRedirection());
		assertThat(messages(second)).anyMatch(m -> m.contains("을 취소했습니다"));
	}

	@Test
	void repeatedReservationNotifiesOnce() throws Exception {
		Resource room = room();
		mvc.perform(post("/reservations").session(login(drafter)).with(csrf()).param("kind", "ROOM")
				.param("resourceId", room.getId().toString()).param("date", DAY.toString())
				.param("startTime", "10:00").param("endTime", "11:00").param("title", "주간 회의")
				.param("repeat", "WEEKLY").param("repeatUntil", DAY.plusWeeks(3).toString())
				.param("skipHolidays", "false").param("attendeeIds", first.getId().toString()))
			.andExpect(status().is3xxRedirection());

		assertThat(messages(first)).filteredOn(m -> m.contains("주간 회의")).hasSize(1)
			.first().asString().contains("4건");
	}

	@Test
	void unknownAttendeeIsRejected() throws Exception {
		Resource room = room();
		mvc.perform(post("/reservations").session(login(drafter)).with(csrf()).param("kind", "ROOM")
				.param("resourceId", room.getId().toString()).param("date", DAY.toString())
				.param("startTime", "10:00").param("endTime", "11:00").param("title", "회의")
				.param("attendeeIds", "99999999"))
			.andExpect(status().isOk())
			.andExpect(content().string(containsString("참석자로 지정할 수 없는 사용자")));
		assertThat(reservationRepository.countByResourceId(room.getId())).isZero();
	}

	// ---------- 7. 일정 알림 ----------

	private ScheduleForm schedule(ScheduleType type, String title, boolean notify, Long... participants) {
		ScheduleForm form = scheduleService.newForm(DAY);
		form.setType(type);
		form.setTitle(title);
		form.setStartTime(LocalTime.of(9, 0));
		form.setEndTime(LocalTime.of(10, 0));
		form.setParticipantIds(List.of(participants));
		form.setNotifyParticipants(notify);
		return form;
	}

	@Test
	void scheduleNotifiesOnlyWhenChecked() {
		String quiet = "조용한 일정" + suffix;
		String loud = "알릴 일정" + suffix;
		scheduleService.save(schedule(ScheduleType.PERSONAL, quiet, false, first.getId()), drafter.getLoginId(), false);
		Long id = scheduleService.save(schedule(ScheduleType.PERSONAL, loud, true, first.getId()), drafter.getLoginId(), false);

		assertThat(messages(first)).noneMatch(m -> m.contains(quiet));
		assertThat(messages(first)).anyMatch(m -> m.contains(loud) && m.contains("등록"));
		assertThat(messages(second)).noneMatch(m -> m.contains(loud));
		assertThat(messages(drafter)).noneMatch(m -> m.contains(loud));

		// 지정된 참여자는 개인일정도 볼 수 있고, 다른 사람은 못 본다
		assertThat(scheduleService.get(id, first.getLoginId(), false).participantNames()).contains(first.getName());
		assertThatThrownBy(() -> scheduleService.get(id, second.getLoginId(), false)).isInstanceOf(AccessDeniedException.class);

		ScheduleForm edit = scheduleService.getForm(id, drafter.getLoginId(), false);
		assertThat(edit.getParticipantIds()).containsExactly(first.getId());
		edit.setNotifyParticipants(true);
		scheduleService.save(edit, drafter.getLoginId(), false);
		assertThat(messages(first)).anyMatch(m -> m.contains(loud) && m.contains("수정"));
	}

	@Test
	void departmentScheduleNotifiesDepartment_companyNotifiesEveryone() {
		String deptTitle = "부서 일정" + suffix;
		String companyTitle = "회사 일정" + suffix;
		Department other = departmentRepository.save(new Department("다른팀" + suffix, null, 0));
		Member outsider = member("outsider", other);
		Member disabled = member("left", dept);
		disabled.changeAccess(Role.USER, false);
		memberRepository.save(disabled);

		scheduleService.save(schedule(ScheduleType.DEPARTMENT, deptTitle, true), drafter.getLoginId(), false);
		assertThat(messages(first)).anyMatch(m -> m.contains(deptTitle));
		assertThat(messages(outsider)).noneMatch(m -> m.contains(deptTitle));
		assertThat(messages(disabled)).noneMatch(m -> m.contains(deptTitle));

		scheduleService.save(schedule(ScheduleType.COMPANY, companyTitle, true), drafter.getLoginId(), true);
		assertThat(messages(outsider)).anyMatch(m -> m.contains(companyTitle));
		assertThat(messages(disabled)).noneMatch(m -> m.contains(companyTitle));
	}

	@Test
	void schedulePagesShowParticipantControls() throws Exception {
		Long id = scheduleService.save(schedule(ScheduleType.PERSONAL, "화면 일정", false, first.getId()), drafter.getLoginId(), false);
		MockHttpSession session = login(drafter);
		mvc.perform(get("/schedule/new").session(session))
			.andExpect(content().string(containsString("참여자 지정")))
			.andExpect(content().string(containsString("참여자에게 알림 보내기")));
		mvc.perform(get("/schedule/{id}/edit", id).session(session)).andExpect(status().isOk());
		mvc.perform(get("/schedule/{id}", id).session(login(first)))
			.andExpect(content().string(containsString(first.getName())));
		mvc.perform(multipart("/schedule").session(session).with(csrf()).param("id", id.toString()).param("type", "PERSONAL")
				.param("title", "화면 일정").param("startDate", DAY.toString()).param("endDate", DAY.toString())
				.param("startTime", "09:00").param("endTime", "10:00")
				.param("participantIds", second.getId().toString()).param("notifyParticipants", "true"))
			.andExpect(status().is3xxRedirection());
		assertThat(messages(second)).anyMatch(m -> m.contains("화면 일정"));
	}

	// ---------- 8. 관리자 메뉴 ----------

	@Test
	void adminMenuShowsOnlyCurrentSectionOnLeft() throws Exception {
		MockHttpSession admin = login("sysadmin", "a");
		mvc.perform(get("/admin/members").session(admin))
			.andExpect(content().string(containsString("인사정보")))
			.andExpect(content().string(containsString("/admin/permissions")))
			.andExpect(content().string(containsString("부서 관리")))
			.andExpect(content().string(not(containsString("권한 관리"))))
			.andExpect(content().string(not(containsString("메뉴 순서"))));
		mvc.perform(get("/admin/menus").session(admin))
			.andExpect(content().string(containsString("메뉴 순서")))
			.andExpect(content().string(not(containsString("부서 관리"))));
	}

	@Test
	void adminMenuFollowsPermissions() throws Exception {
		String html = mvc.perform(get("/admin/boards").session(login(member("boardman", dept, Permission.BOARD_MANAGER))))
			.andExpect(status().isOk())
			.andReturn().getResponse().getContentAsString();
		assertThat(html).contains("게시판 관리").doesNotContain("인사정보", "전자결재", "/admin/rooms", "/admin/menus");
	}

	@Test
	void attendancePagesRenderInsideAdminAndAttendanceMenus() throws Exception {
		MockHttpSession admin = login("sysadmin", "a");
		mvc.perform(get("/admin/attendance/holidays").session(admin))
			.andExpect(status().isOk())
			.andExpect(content().string(containsString("admin-sidebar")))
			.andExpect(content().string(containsString("/admin/attendance/holidays/fixed")))
			.andExpect(content().string(not(containsString("attend-nav"))));
		mvc.perform(get("/admin/attendance/leave").session(admin))
			.andExpect(status().isOk())
			.andExpect(content().string(containsString("admin-sidebar")))
			.andExpect(content().string(containsString("/admin/attendance/leave/grant")))
			.andExpect(content().string(not(containsString("attend-nav"))));
		mvc.perform(post("/admin/attendance/holidays/fixed").session(admin).with(csrf()).param("year", "2040"))
			.andExpect(redirectedUrl("/admin/attendance/holidays?year=2040"));

		MockHttpSession manager = login(member("attend", dept, Permission.ATTENDANCE_MANAGER));
		mvc.perform(get("/attendance/manage/holidays").session(manager))
			.andExpect(status().isOk())
			.andExpect(content().string(not(containsString("admin-sidebar"))))
			.andExpect(content().string(containsString("/attendance/manage/holidays/fixed")));
		mvc.perform(get("/attendance/manage/leave").session(manager)).andExpect(status().isOk());
		mvc.perform(get("/admin/attendance/holidays").session(manager)).andExpect(status().isForbidden());
		mvc.perform(post("/attendance/manage/holidays/fixed").session(manager).with(csrf()).param("year", "2041"))
			.andExpect(redirectedUrl("/attendance/manage/holidays?year=2041"));
	}

	// ---------- 사람 목록을 조직도처럼 ----------

	@Test
	void memberAdminShowsSelectedDepartmentLikeOrgChart() throws Exception {
		Department other = departmentRepository.save(new Department("다른팀" + suffix, null, 1));
		Member outsider = member("outsider", other);
		MockHttpSession admin = login("sysadmin", "a");

		mvc.perform(get("/admin/members").session(admin).param("dept", dept.getId().toString()))
			.andExpect(status().isOk())
			.andExpect(content().string(containsString("org-tree")))
			.andExpect(content().string(containsString(other.getName())))
			.andExpect(content().string(containsString(drafter.getLoginId())))
			.andExpect(content().string(not(containsString(outsider.getLoginId()))));

		// 검색은 보던 부서와 상관없이 회사 전체에서 찾는다
		mvc.perform(get("/admin/members").session(admin).param("dept", dept.getId().toString())
				.param("q", outsider.getLoginId()))
			.andExpect(content().string(containsString("검색 결과")))
			.andExpect(content().string(containsString(outsider.getLoginId())))
			.andExpect(content().string(not(containsString(drafter.getLoginId()))));

		// 부서가 없는 사용자도 빠지지 않는다
		mvc.perform(get("/admin/members").session(admin).param("dept", "0"))
			.andExpect(content().string(containsString("부서 없음")))
			.andExpect(content().string(containsString("sysadmin")));
	}

	@Test
	void leaveManageShowsSelectedDepartmentAndStaysThereAfterSaving() throws Exception {
		Department other = departmentRepository.save(new Department("다른팀" + suffix, null, 1));
		Member outsider = member("outsider", other);
		MockHttpSession admin = login("sysadmin", "a");

		mvc.perform(get("/admin/attendance/leave").session(admin).param("dept", dept.getId().toString()))
			.andExpect(status().isOk())
			.andExpect(content().string(containsString("org-tree")))
			.andExpect(content().string(containsString(drafter.getName())))
			.andExpect(content().string(not(containsString(outsider.getName()))));

		mvc.perform(post("/admin/attendance/leave/grant").session(admin).with(csrf())
				.param("member", drafter.getId().toString()).param("year", "2042").param("days", "20")
				.param("dept", dept.getId().toString()))
			.andExpect(redirectedUrl("/admin/attendance/leave?year=2042&dept=" + dept.getId()));
	}

	@Test
	void personInDisabledDepartmentStaysVisibleOnManageScreens() throws Exception {
		Department closed = new Department("폐지팀" + suffix, null, 1);
		closed.update(closed.getName(), null, 1, false);
		departmentRepository.save(closed);
		Member left = member("left", closed);
		MockHttpSession admin = login("sysadmin", "a");

		mvc.perform(get("/admin/members").session(admin).param("dept", closed.getId().toString()))
			.andExpect(content().string(containsString("사용 안 함")))
			.andExpect(content().string(containsString(left.getLoginId())));
		mvc.perform(get("/admin/attendance/leave").session(admin).param("dept", closed.getId().toString()))
			.andExpect(content().string(containsString(left.getName())));
	}
}
