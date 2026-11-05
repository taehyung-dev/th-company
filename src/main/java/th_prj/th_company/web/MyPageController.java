package th_prj.th_company.web;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import jakarta.validation.Valid;
import th_prj.th_company.approval.ApprovalBox;
import th_prj.th_company.approval.ApprovalService;
import th_prj.th_company.attendance.AttendanceService;
import th_prj.th_company.common.BusinessException;
import th_prj.th_company.member.MyPageService;
import th_prj.th_company.leave.LeaveService;
import th_prj.th_company.member.MyProfileForm;
import th_prj.th_company.member.PasswordChangeForm;
import th_prj.th_company.member.ProfilePhotoService;
import th_prj.th_company.reservation.ReservationKind;
import th_prj.th_company.reservation.ReservationService;
import th_prj.th_company.reservation.ReservationViews.Item;
import th_prj.th_company.schedule.ScheduleService;

/**
 * 마이페이지 (상단 오른쪽 사람 아이콘): 내 정보와 나의 현황, 정보 수정, 비밀번호 변경.
 */
@Controller
@RequestMapping("/my")
public class MyPageController {

	/** 나의 현황에 보일 다가오는 예약 수 */
	private static final int UPCOMING_LIMIT = 5;

	private final MyPageService myPageService;
	private final ProfilePhotoService profilePhotoService;
	private final ApprovalService approvalService;
	private final AttendanceService attendanceService;
	private final ScheduleService scheduleService;
	private final ReservationService reservationService;
	private final LeaveService leaveService;

	public MyPageController(MyPageService myPageService, ProfilePhotoService profilePhotoService,
			ApprovalService approvalService, AttendanceService attendanceService, ScheduleService scheduleService,
			ReservationService reservationService, LeaveService leaveService) {
		this.myPageService = myPageService;
		this.profilePhotoService = profilePhotoService;
		this.approvalService = approvalService;
		this.attendanceService = attendanceService;
		this.scheduleService = scheduleService;
		this.reservationService = reservationService;
		this.leaveService = leaveService;
	}

	@GetMapping
	public String index(Authentication authentication, Model model) {
		String loginId = authentication.getName();
		model.addAttribute("profile", myPageService.profile(loginId));
		model.addAttribute("pendingCount", approvalService.counts(loginId).getOrDefault(ApprovalBox.PENDING.getPath(), 0L));
		model.addAttribute("attendance", attendanceService.today(loginId));
		model.addAttribute("scheduleCount", scheduleService.today(loginId).size());
		model.addAttribute("leave", leaveService.summary(loginId, leaveService.currentYear()));
		List<Item> rooms = reservationService.myUpcoming(ReservationKind.ROOM, loginId);
		List<Item> resources = reservationService.myUpcoming(ReservationKind.RESOURCE, loginId);
		model.addAttribute("roomCount", rooms.size());
		model.addAttribute("resourceCount", resources.size());
		model.addAttribute("upcoming", Stream.concat(rooms.stream(), resources.stream())
			.sorted(Comparator.comparing(Item::startAt))
			.limit(UPCOMING_LIMIT)
			.toList());
		model.addAttribute("menu", "info");
		return "my/index";
	}

	@GetMapping("/edit")
	public String editForm(Authentication authentication, Model model) {
		model.addAttribute("form", myPageService.editForm(authentication.getName()));
		return renderEdit(authentication, model);
	}

	@PostMapping("/edit")
	public String edit(@Valid @ModelAttribute("form") MyProfileForm form, BindingResult bindingResult,
			@RequestParam(required = false) MultipartFile photo, @RequestParam(defaultValue = "false") boolean removePhoto,
			Authentication authentication, Model model, RedirectAttributes redirect) {
		if (bindingResult.hasErrors()) {
			return renderEdit(authentication, model);
		}
		try {
			byte[] jpeg = profilePhotoService.convert(photo);
			Long id = myPageService.updateProfile(authentication.getName(), form);
			profilePhotoService.apply(id, jpeg, removePhoto);
		} catch (BusinessException e) {
			model.addAttribute("error", e.getMessage());
			return renderEdit(authentication, model);
		}
		redirect.addFlashAttribute("message", "내 정보를 저장했습니다.");
		return "redirect:/my";
	}

	@GetMapping("/password")
	public String passwordForm(Model model) {
		model.addAttribute("form", new PasswordChangeForm());
		model.addAttribute("menu", "password");
		return "my/password";
	}

	@PostMapping("/password")
	public String changePassword(@Valid @ModelAttribute("form") PasswordChangeForm form, BindingResult bindingResult,
			Authentication authentication, Model model, RedirectAttributes redirect) {
		model.addAttribute("menu", "password");
		if (bindingResult.hasErrors()) {
			return "my/password";
		}
		try {
			myPageService.changePassword(authentication.getName(), form);
		} catch (BusinessException e) {
			model.addAttribute("error", e.getMessage());
			return "my/password";
		}
		redirect.addFlashAttribute("message", "비밀번호를 바꿨습니다. 다음 로그인부터 새 비밀번호를 쓰세요.");
		return "redirect:/my";
	}

	private String renderEdit(Authentication authentication, Model model) {
		model.addAttribute("profile", myPageService.profile(authentication.getName()));
		model.addAttribute("menu", "edit");
		return "my/edit";
	}
}
