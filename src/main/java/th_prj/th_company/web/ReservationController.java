package th_prj.th_company.web;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import jakarta.validation.Valid;
import th_prj.th_company.common.BusinessException;
import th_prj.th_company.common.SecurityUtils;
import th_prj.th_company.reservation.RepeatRule;
import th_prj.th_company.reservation.ReservationForm;
import th_prj.th_company.reservation.ReservationKind;
import th_prj.th_company.reservation.ReservationService;
import th_prj.th_company.reservation.ReservationViews.Item;
import th_prj.th_company.reservation.ReservationViews.Saved;
import th_prj.th_company.reservation.Resource;
import th_prj.th_company.reservation.ResourceType;

/**
 * 회의실 예약(/reservations/rooms)과 자원 예약(/reservations/resources): 일간 현황, 내 예약, 예약 등록.
 * 예약 한 건(/reservations/{id})의 상세·수정·취소는 공통이고, 구분은 예약한 자원에서 정해진다.
 */
@Controller
@RequestMapping("/reservations")
public class ReservationController {

	private static final String KIND = "{kind:rooms|resources}";

	private final ReservationService reservationService;

	public ReservationController(ReservationService reservationService) {
		this.reservationService = reservationService;
	}

	/** 예전 주소: 회의실 예약으로 */
	@GetMapping
	public String index() {
		return "redirect:/reservations/rooms";
	}

	@GetMapping("/" + KIND)
	public String day(@PathVariable("kind") String path,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
			@RequestParam(required = false) ResourceType type, Authentication authentication, Model model) {
		ReservationKind kind = kind(path);
		model.addAttribute("board", reservationService.day(kind, date == null ? LocalDate.now() : date, type, authentication.getName()));
		return common(kind, "day", authentication, model, "reservation/day");
	}

	@GetMapping("/" + KIND + "/my")
	public String my(@PathVariable("kind") String path, Authentication authentication, Model model) {
		ReservationKind kind = kind(path);
		model.addAttribute("upcoming", reservationService.myUpcoming(kind, authentication.getName()));
		model.addAttribute("past", reservationService.myPast(kind, authentication.getName()));
		return common(kind, "my", authentication, model, "reservation/my");
	}

	@GetMapping("/" + KIND + "/new")
	public String createForm(@PathVariable("kind") String path, @RequestParam(required = false) Long resource,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.TIME) LocalTime start,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.TIME) LocalTime end,
			Authentication authentication, Model model) {
		ReservationForm form = reservationService.newForm(kind(path), resource, date, start);
		if (endDate != null && !endDate.isBefore(form.getDate())) {
			form.setEndDate(endDate);
		}
		if (end != null) {
			form.setEndTime(end);
		}
		model.addAttribute("form", form);
		return renderForm(authentication, model);
	}

	@GetMapping("/{id:\\d+}")
	public String view(@PathVariable Long id, Authentication authentication, Model model) {
		Item item = reservationService.get(id, authentication.getName(), managed(authentication));
		model.addAttribute("item", item);
		model.addAttribute("files", reservationService.files(item));
		return common(item.kind(), "day", authentication, model, "reservation/view");
	}

	@GetMapping("/{id:\\d+}/edit")
	public String editForm(@PathVariable Long id, Authentication authentication, Model model) {
		model.addAttribute("form", reservationService.getForm(id, authentication.getName(), managed(authentication)));
		return renderForm(authentication, model);
	}

	@PostMapping
	public String save(@Valid @ModelAttribute("form") ReservationForm form, BindingResult bindingResult,
			@RequestParam(required = false) List<MultipartFile> uploads,
			Authentication authentication, Model model, RedirectAttributes redirect) {
		if (bindingResult.hasErrors()) {
			return renderForm(authentication, model);
		}
		Saved saved;
		try {
			saved = reservationService.save(form, authentication.getName(), managed(authentication), uploads);
		} catch (BusinessException e) {
			model.addAttribute("error", e.getMessage());
			return renderForm(authentication, model);
		}
		String done = form.getId() != null ? "예약을 수정했습니다."
			: saved.count() == 1 && saved.skipped() == 0 ? "예약했습니다."
			: saved.count() + "건을 예약했습니다." + (saved.skipped() > 0 ? " 겹치는 " + saved.skipped() + "일은 건너뛰었습니다." : "");
		redirect.addFlashAttribute("message", done);
		return "redirect:/reservations/" + form.getKind().getPath() + "?date=" + form.getDate();
	}

	/** back=my 이면 내 예약으로, 아니면 그날 현황으로. scope=following 이면 반복 예약의 이후 일정까지 */
	@PostMapping("/{id:\\d+}/delete")
	public String cancel(@PathVariable Long id, @RequestParam(required = false) String back,
			@RequestParam(required = false) String scope,
			Authentication authentication, RedirectAttributes redirect) {
		Item item = reservationService.get(id, authentication.getName(), managed(authentication));
		try {
			if ("following".equals(scope)) {
				int count = reservationService.cancelFollowing(id, authentication.getName(), managed(authentication));
				redirect.addFlashAttribute("message", "반복 예약 " + count + "건을 취소했습니다.");
			} else {
				reservationService.cancel(id, authentication.getName(), managed(authentication));
				redirect.addFlashAttribute("message", "예약을 취소했습니다.");
			}
		} catch (BusinessException e) {
			redirect.addFlashAttribute("error", e.getMessage());
			return "redirect:/reservations/" + id;
		}
		String base = "/reservations/" + item.kind().getPath();
		return "my".equals(back) ? "redirect:" + base + "/my"
			: "redirect:" + base + "?date=" + item.startAt().toLocalDate();
	}

	private String renderForm(Authentication authentication, Model model) {
		ReservationForm form = (ReservationForm) model.getAttribute("form");
		// 종류별로 묶어서 (optgroup)
		Map<ResourceType, List<Resource>> groups = new EnumMap<>(ResourceType.class);
		for (Resource r : reservationService.selectableResources(form.getKind(), form.getResourceId())) {
			groups.computeIfAbsent(r.getType(), k -> new ArrayList<>()).add(r);
		}
		model.addAttribute("resourceGroups", groups);
		boolean multiDay = form.getKind() == ReservationKind.RESOURCE;
		model.addAttribute("taken", reservationService.taken(form.getResourceId(), form.getDate(),
			multiDay ? form.getEndDate() : null, form.getId(), authentication.getName()));
		model.addAttribute("multiDay", multiDay);
		model.addAttribute("repeatRules", RepeatRule.values());
		model.addAttribute("files", form.getId() == null ? List.of() : reservationService.ownFiles(form.getId()));
		return common(form.getKind(), "day", authentication, model, "reservation/form");
	}

	/** 화면 공통: 구분, 좌측 메뉴 선택, 그 구분의 관리 권한 */
	private static String common(ReservationKind kind, String menu, Authentication authentication, Model model, String view) {
		model.addAttribute("kind", kind);
		model.addAttribute("menu", menu);
		model.addAttribute("canManage", SecurityUtils.can(authentication, kind.getPermission()));
		return view;
	}

	/** 관리 권한이 있는 구분 (관리자는 모두) */
	private static Set<ReservationKind> managed(Authentication authentication) {
		Set<ReservationKind> set = EnumSet.noneOf(ReservationKind.class);
		for (ReservationKind k : ReservationKind.values()) {
			if (SecurityUtils.can(authentication, k.getPermission())) {
				set.add(k);
			}
		}
		return set;
	}

	private static ReservationKind kind(String path) {
		ReservationKind kind = ReservationKind.fromPath(path);
		if (kind == null) {
			throw new ResponseStatusException(HttpStatus.NOT_FOUND);
		}
		return kind;
	}
}
