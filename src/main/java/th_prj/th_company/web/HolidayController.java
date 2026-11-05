package th_prj.th_company.web;

import java.time.Clock;
import java.time.LocalDate;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import th_prj.th_company.common.BusinessException;
import th_prj.th_company.holiday.HolidayForm;
import th_prj.th_company.holiday.HolidayService;

/**
 * 공휴일 관리. 근태 메뉴(근태관리자와 관리자)와 관리자 화면(관리자)에서 같이 쓴다 (SecurityConfig).
 */
@Controller
@RequestMapping({HolidayController.ATTENDANCE_PATH, HolidayController.ADMIN_PATH})
public class HolidayController {

	static final String ATTENDANCE_PATH = "/attendance/manage/holidays";
	static final String ADMIN_PATH = "/admin/attendance/holidays";

	private final HolidayService holidayService;
	private final Clock clock;

	public HolidayController(HolidayService holidayService, Clock clock) {
		this.holidayService = holidayService;
		this.clock = clock;
	}

	@GetMapping
	public String list(@RequestParam(required = false) Integer year, HttpServletRequest request, Model model) {
		model.addAttribute("form", new HolidayForm());
		return render(year, request, model);
	}

	@PostMapping
	public String add(@Valid @ModelAttribute("form") HolidayForm form, BindingResult bindingResult,
			HttpServletRequest request, Model model, RedirectAttributes redirect) {
		Integer year = form.getDate() == null ? null : form.getDate().getYear();
		if (bindingResult.hasErrors()) {
			return render(year, request, model);
		}
		try {
			holidayService.add(form);
		} catch (BusinessException e) {
			model.addAttribute("error", e.getMessage());
			return render(year, request, model);
		}
		redirect.addFlashAttribute("message", "공휴일을 추가했습니다.");
		return "redirect:" + base(request) + "?year=" + year;
	}

	@PostMapping("/fixed")
	public String addFixed(@RequestParam int year, HttpServletRequest request, RedirectAttributes redirect) {
		int added = holidayService.addFixed(year);
		redirect.addFlashAttribute("message", added == 0 ? "이미 모두 등록되어 있습니다." : "양력 공휴일 " + added + "건을 추가했습니다.");
		return "redirect:" + base(request) + "?year=" + year;
	}

	@PostMapping("/{id}/delete")
	public String delete(@PathVariable Long id, @RequestParam int year, HttpServletRequest request, RedirectAttributes redirect) {
		holidayService.delete(id);
		redirect.addFlashAttribute("message", "공휴일을 삭제했습니다.");
		return "redirect:" + base(request) + "?year=" + year;
	}

	private static String base(HttpServletRequest request) {
		return LeaveManageController.fromAdmin(request) ? ADMIN_PATH : ATTENDANCE_PATH;
	}

	private String render(Integer year, HttpServletRequest request, Model model) {
		int y = year == null ? LocalDate.now(clock).getYear() : year;
		model.addAttribute("menu", "holidays");
		model.addAttribute("year", y);
		model.addAttribute("holidays", holidayService.list(y));
		model.addAttribute("base", base(request));
		return LeaveManageController.fromAdmin(request) ? "admin/holidays" : "attendance/holidays";
	}
}
