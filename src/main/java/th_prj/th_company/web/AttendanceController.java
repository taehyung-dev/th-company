package th_prj.th_company.web;

import java.time.LocalDate;
import java.time.YearMonth;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
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
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import jakarta.validation.Valid;
import th_prj.th_company.attendance.AttendanceBoard;
import th_prj.th_company.attendance.AttendanceEditForm;
import th_prj.th_company.attendance.AttendanceService;
import th_prj.th_company.common.BusinessException;
import th_prj.th_company.common.XlsxWriter;
import th_prj.th_company.org.OrgChartService;

/**
 * 근태관리. 내 근태는 누구나, 전체 근태 현황과 정정(/attendance/manage/**)은 근태관리자와 관리자만
 * (SecurityConfig 에서 막는다).
 */
@Controller
@RequestMapping("/attendance")
public class AttendanceController {

	private final AttendanceService attendanceService;
	private final OrgChartService orgChartService;

	public AttendanceController(AttendanceService attendanceService, OrgChartService orgChartService) {
		this.attendanceService = attendanceService;
		this.orgChartService = orgChartService;
	}

	/** 내 근태 (월별). month=2026-10 */
	@GetMapping
	public String my(@RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM") YearMonth month,
			Authentication authentication, Model model) {
		model.addAttribute("menu", "my");
		model.addAttribute("today", attendanceService.today(authentication.getName()));
		model.addAttribute("monthView", attendanceService.myMonth(authentication.getName(), month));
		addRules(model);
		return "attendance/month";
	}

	/** [퇴근] 버튼. 홈 화면과 근태관리 화면에서 누른다 (back=attendance 면 근태관리로 돌아감) */
	@PostMapping("/check-out")
	public String checkOut(@RequestParam(required = false) String back, Authentication authentication,
			RedirectAttributes redirect) {
		try {
			attendanceService.checkOut(authentication.getName());
			redirect.addFlashAttribute("attendanceMessage", "퇴근 시간을 기록했습니다.");
		} catch (BusinessException e) {
			redirect.addFlashAttribute("attendanceError", e.getMessage());
		}
		return "attendance".equals(back) ? "redirect:/attendance" : "redirect:/";
	}

	// ---------- 근태관리자 ----------

	/** 하루 전체 근태 현황. 위 요약은 회사 전체, 아래 목록은 조직도처럼 고른 부서 */
	@GetMapping("/manage")
	public String manage(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
			@RequestParam(required = false) Long dept, Authentication authentication, Model model) {
		AttendanceBoard board = attendanceService.board(date, null);
		model.addAttribute("menu", "manage");
		model.addAttribute("board", board);
		model.addAttribute("org", orgChartService.deptView(board.rows(), AttendanceBoard.Row::deptId, dept,
			authentication.getName()));
		addRules(model);
		return "attendance/manage";
	}

	/** 그날 전체 근태 현황을 회사 전체(부서 순서)로 내려받는다 */
	@GetMapping("/manage/excel")
	public ResponseEntity<byte[]> manageExcel(
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
		AttendanceBoard board = attendanceService.board(date, null);
		XlsxWriter x = new XlsxWriter();
		XlsxWriter.Sheet s = x.sheet(board.date().toString(), 16, 10, 9, 9, 8, 8, 10, 22);
		s.header("부서", "이름", "직급", "직책", "출근", "퇴근", "근무시간", "상태");
		for (AttendanceBoard.Row r : board.rows()) {
			s.row(r.deptName(), r.name(), r.gradeName(), r.dutyName(), r.day().displayCheckIn(), r.day().displayCheckOut(),
				r.day().displayWorked(), r.day().statusText());
		}
		return x.download("전체근태현황_" + board.date() + ".xlsx");
	}

	/** 직원 한 명의 월별 근태 */
	@GetMapping("/manage/members/{id}")
	public String member(@PathVariable Long id,
			@RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM") YearMonth month, Model model) {
		model.addAttribute("menu", "manage");
		model.addAttribute("monthView", attendanceService.month(id, month));
		model.addAttribute("managing", true);
		addRules(model);
		return "attendance/month";
	}

	@GetMapping("/manage/edit")
	public String editForm(@RequestParam Long member,
			@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date, Model model) {
		model.addAttribute("form", attendanceService.editForm(member, date));
		return renderEdit(model, member);
	}

	@PostMapping("/manage/edit")
	public String edit(@Valid @ModelAttribute("form") AttendanceEditForm form, BindingResult bindingResult,
			Authentication authentication, Model model, RedirectAttributes redirect) {
		if (bindingResult.hasErrors()) {
			return renderEdit(model, form.getMemberId());
		}
		try {
			attendanceService.correct(form, authentication.getName());
		} catch (BusinessException e) {
			model.addAttribute("error", e.getMessage());
			return renderEdit(model, form.getMemberId());
		}
		redirect.addFlashAttribute("message", "근태를 정정했습니다.");
		return "redirect:/attendance/manage/members/" + form.getMemberId() + "?month=" + YearMonth.from(form.getDate());
	}

	private String renderEdit(Model model, Long memberId) {
		model.addAttribute("menu", "manage");
		model.addAttribute("memberLabel", attendanceService.memberLabel(memberId));
		addRules(model);
		return "attendance/edit";
	}

	private void addRules(Model model) {
		model.addAttribute("workStart", attendanceService.getWorkStart());
		model.addAttribute("workEnd", attendanceService.getWorkEnd());
	}
}
