package th_prj.th_company.web;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import th_prj.th_company.approval.ApprovalService;
import th_prj.th_company.attendance.AttendanceService;
import th_prj.th_company.board.PostService;
import th_prj.th_company.member.MyPageService;
import th_prj.th_company.schedule.ScheduleService;

@Controller
public class MainController {

	private static final DateTimeFormatter TODAY_FORMAT =
		DateTimeFormatter.ofPattern("yyyy년 M월 d일 EEEE", Locale.KOREAN);

	private final PostService postService;
	private final ApprovalService approvalService;
	private final ScheduleService scheduleService;
	private final AttendanceService attendanceService;
	private final MyPageService myPageService;

	public MainController(PostService postService, ApprovalService approvalService, ScheduleService scheduleService,
			AttendanceService attendanceService, MyPageService myPageService) {
		this.postService = postService;
		this.approvalService = approvalService;
		this.scheduleService = scheduleService;
		this.attendanceService = attendanceService;
		this.myPageService = myPageService;
	}

	@GetMapping("/")
	public String main(Authentication authentication, Model model) {
		String loginId = authentication.getName();
		model.addAttribute("today", LocalDate.now().format(TODAY_FORMAT));
		model.addAttribute("notices", postService.latestNotices(5));
		model.addAttribute("recentPosts", postService.latestPosts(5));
		model.addAttribute("pendingDocs", approvalService.pendingForHome(loginId, 5));
		model.addAttribute("todaySchedules", scheduleService.today(loginId));
		model.addAttribute("attendance", attendanceService.today(loginId));
		// 홈 포털 왼쪽: 내 정보, 결재 바로가기 숫자. 오른쪽 일정 포틀릿: 이번 달 미니 달력
		model.addAttribute("me", myPageService.profile(loginId));
		model.addAttribute("approvalCounts", approvalService.counts(loginId));
		model.addAttribute("calendar", scheduleService.month(YearMonth.now(), loginId));
		return "main";
	}
}
