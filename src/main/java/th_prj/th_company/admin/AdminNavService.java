package th_prj.th_company.admin;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

import th_prj.th_company.admin.AdminNav.Item;
import th_prj.th_company.admin.AdminNav.Section;
import th_prj.th_company.common.SecurityUtils;
import th_prj.th_company.member.Permission;
import th_prj.th_company.org.HrCodeType;

/**
 * 관리자 화면 메뉴를 만든다. 권한이 있는 메뉴만 담고, 구분은 관리자가 정한 순서(AdminMenuService)로 놓는다.
 * 메뉴가 하나도 없는 구분은 상단에 나오지 않는다.
 */
@Component
public class AdminNavService {

	private final AdminMenuService adminMenuService;

	public AdminNavService(AdminMenuService adminMenuService) {
		this.adminMenuService = adminMenuService;
	}

	public AdminNav build(Authentication authentication) {
		boolean admin = SecurityUtils.isAdmin(authentication);
		Map<AdminSection, List<Item>> items = new EnumMap<>(AdminSection.class);
		if (admin) {
			List<Item> hr = new ArrayList<>();
			hr.add(new Item("members", "사용자 관리", "/admin/members"));
			hr.add(new Item("departments", "부서 관리", "/admin/departments"));
			for (HrCodeType t : HrCodeType.values()) {
				hr.add(new Item("codes-" + t.getPath(), t.getLabel() + " 관리", "/admin/codes/" + t.getPath()));
			}
			items.put(AdminSection.HR, hr);
			items.put(AdminSection.PERMISSION, List.of(new Item("permissions", "권한 관리", "/admin/permissions")));
			items.put(AdminSection.ATTENDANCE, List.of(
				new Item("holidays", "공휴일 관리", "/admin/attendance/holidays"),
				new Item("leave-manage", "휴가 현황", "/admin/attendance/leave")));
			items.put(AdminSection.SCREEN, List.of(
				new Item("menus", "사용자 메뉴 순서", "/admin/menus"),
				new Item("admin-menus", "관리자 메뉴 순서", "/admin/menus/admin")));
		}
		if (SecurityUtils.can(authentication, Permission.BOARD_MANAGER)) {
			items.put(AdminSection.BOARD, List.of(new Item("boards", "게시판 관리", "/admin/boards")));
		}
		if (SecurityUtils.can(authentication, Permission.APPROVAL_MANAGER)) {
			items.put(AdminSection.APPROVAL, List.of(new Item("approval-templates", "결재 양식 관리", "/admin/approval-templates")));
		}
		List<Item> reservation = new ArrayList<>();
		if (SecurityUtils.can(authentication, Permission.ROOM_MANAGER)) {
			reservation.add(new Item("rooms", "회의실 관리", "/admin/rooms"));
		}
		if (SecurityUtils.can(authentication, Permission.RESOURCE_MANAGER)) {
			reservation.add(new Item("resources", "자원 관리", "/admin/resources"));
		}
		if (!reservation.isEmpty()) {
			items.put(AdminSection.RESERVATION, reservation);
		}

		return new AdminNav(adminMenuService.ordered().stream()
			.filter(items::containsKey)
			.map(s -> new Section(s.getLabel(), items.get(s)))
			.toList());
	}
}
