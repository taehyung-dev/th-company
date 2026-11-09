package th_prj.th_company.web;

import java.util.Comparator;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import jakarta.servlet.http.HttpServletRequest;
import th_prj.th_company.common.BusinessException;
import th_prj.th_company.common.XlsxWriter;
import th_prj.th_company.leave.LeaveService;
import th_prj.th_company.leave.LeaveViews;
import th_prj.th_company.org.OrgChartService;

/**
 * 전체 휴가 현황. 근태 메뉴(근태관리자와 관리자)와 관리자 화면(관리자)에서 같이 쓴다 (SecurityConfig).
 */
@Controller
@RequestMapping({LeaveManageController.ATTENDANCE_PATH, LeaveManageController.ADMIN_PATH})
public class LeaveManageController {

	static final String ATTENDANCE_PATH = "/attendance/manage/leave";
	static final String ADMIN_PATH = "/admin/attendance/leave";

	private final LeaveService leaveService;
	private final OrgChartService orgChartService;

	public LeaveManageController(LeaveService leaveService, OrgChartService orgChartService) {
		this.leaveService = leaveService;
		this.orgChartService = orgChartService;
	}

	/** 관리자 화면 주소로 들어왔으면 관리자 화면 틀로 보여 준다 */
	static boolean fromAdmin(HttpServletRequest request) {
		return request.getRequestURI().substring(request.getContextPath().length()).startsWith("/admin/");
	}

	/** 직원별 연차는 조직도처럼 부서별로 보고, 휴가 신청은 사용 중지된 사람 것까지 회사 전체를 본다 */
	@GetMapping
	public String manage(@RequestParam(required = false) Integer year, @RequestParam(required = false) Long dept,
			Authentication authentication, HttpServletRequest request, Model model) {
		int y = year == null ? leaveService.currentYear() : year;
		model.addAttribute("menu", "leave-manage");
		model.addAttribute("year", y);
		model.addAttribute("org", orgChartService.deptView(leaveService.memberRows(y), LeaveViews.MemberRow::deptId,
			dept, authentication.getName()));
		model.addAttribute("requests", leaveService.allRequests(y));
		model.addAttribute("base", base(request));
		return fromAdmin(request) ? "admin/leave-manage" : "attendance/leave-manage";
	}

	/** 그해 직원별 연차와 휴가 신청을 회사 전체로 내려받는다 (시트 두 개) */
	@GetMapping("/excel")
	public ResponseEntity<byte[]> excel(@RequestParam(required = false) Integer year) {
		int y = year == null ? leaveService.currentYear() : year;
		Comparator<Long> deptOrder = orgChartService.departmentOrder();
		XlsxWriter x = new XlsxWriter();
		XlsxWriter.Sheet members = x.sheet("직원별 연차", 16, 10, 9, 9, 12, 8, 8, 9, 8);
		members.header("부서", "이름", "직급", "직책", "입사일", "연차", "사용", "결재 중", "남음");
		leaveService.memberRows(y).stream()
			.sorted(Comparator.comparing(LeaveViews.MemberRow::deptId, deptOrder))
			.forEach(m -> members.row(m.deptName(), m.name(), m.gradeName(), m.dutyName(), m.displayHireDate(),
				m.summary().granted(), m.summary().used(), m.summary().pending(), m.summary().remaining()));
		XlsxWriter.Sheet requests = x.sheet("휴가 신청", 16, 10, 10, 24, 8, 30, 9);
		requests.header("부서", "이름", "종류", "기간", "일수", "사유", "상태");
		for (LeaveViews.Row r : leaveService.allRequests(y)) {
			requests.row(r.deptName(), r.memberName(), r.type().getLabel(), r.period(), r.days(), r.reason(),
				r.status().getLabel());
		}
		return x.download("전체휴가현황_" + y + ".xlsx");
	}

	/** days 를 비우면 입사일 기준으로 되돌린다 */
	@PostMapping("/grant")
	public String grant(@RequestParam Long member, @RequestParam int year, @RequestParam(required = false) Double days,
			@RequestParam(required = false) Long dept, HttpServletRequest request, RedirectAttributes redirect) {
		try {
			leaveService.setGranted(member, year, days);
			redirect.addFlashAttribute("message", "연차 일수를 저장했습니다.");
		} catch (BusinessException e) {
			redirect.addFlashAttribute("error", e.getMessage());
		}
		return back(request, year, dept);
	}

	@PostMapping("/{id}/cancel")
	public String cancelByManager(@PathVariable Long id, @RequestParam int year,
			@RequestParam(required = false) Long dept, Authentication authentication,
			HttpServletRequest request, RedirectAttributes redirect) {
		try {
			leaveService.cancelByManager(id, authentication.getName());
			redirect.addFlashAttribute("message", "휴가를 취소했습니다.");
		} catch (BusinessException e) {
			redirect.addFlashAttribute("error", e.getMessage());
		}
		return back(request, year, dept);
	}

	/** 저장한 뒤에도 보던 부서에 머문다 */
	private static String back(HttpServletRequest request, int year, Long dept) {
		return "redirect:" + base(request) + "?year=" + year + (dept == null ? "" : "&dept=" + dept);
	}

	private static String base(HttpServletRequest request) {
		return fromAdmin(request) ? ADMIN_PATH : ATTENDANCE_PATH;
	}
}
