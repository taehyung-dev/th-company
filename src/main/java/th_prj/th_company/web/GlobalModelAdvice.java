package th_prj.th_company.web;

import java.util.List;

import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

import th_prj.th_company.common.SecurityUtils;
import th_prj.th_company.member.Member;
import th_prj.th_company.member.MemberRepository;
import th_prj.th_company.member.Permission;
import th_prj.th_company.menu.MainMenu;
import th_prj.th_company.menu.MenuService;
import th_prj.th_company.notification.NotificationService;

/**
 * 모든 화면 공통 모델: 로그인 아이디(권한 비교용), 이름(상단 표시용), 관리자 메뉴 노출 여부.
 */
@ControllerAdvice
public class GlobalModelAdvice {

	private final MemberRepository memberRepository;
	private final MenuService menuService;
	private final NotificationService notificationService;

	public GlobalModelAdvice(MemberRepository memberRepository, MenuService menuService,
			NotificationService notificationService) {
		this.memberRepository = memberRepository;
		this.menuService = menuService;
		this.notificationService = notificationService;
	}

	/** 상단 종 아이콘의 안 읽은 알림 수 */
	@ModelAttribute("unreadNotifications")
	public long unreadNotifications(Authentication authentication) {
		return authentication == null ? 0 : notificationService.unreadCount(authentication.getName());
	}

	/** 로그인 아이디. 작성자 비교 등 권한 판단에 쓴다. */
	@ModelAttribute("currentUser")
	public String currentUser(Authentication authentication) {
		return authentication == null ? null : authentication.getName();
	}

	/** 상단 "홍길동 님" 표시용. 관리자가 이름을 바꾸면 다음 화면부터 바로 반영된다. */
	@ModelAttribute("currentUserName")
	public String currentUserName(Authentication authentication) {
		if (authentication == null) {
			return null;
		}
		return memberRepository.findByLoginId(authentication.getName())
			.map(Member::getName)
			.orElse(authentication.getName());
	}

	/** 상단 메뉴 (관리자가 정한 순서) */
	@ModelAttribute("mainMenus")
	public List<MainMenu> mainMenus() {
		return menuService.ordered();
	}

	@ModelAttribute("isAdmin")
	public boolean isAdmin(Authentication authentication) {
		return SecurityUtils.isAdmin(authentication);
	}

	/** 화면에서 관리 기능(버튼, 메뉴)을 보일지 정할 때 쓴다. 관리자는 모두 true */
	@ModelAttribute("can")
	public Can can(Authentication authentication) {
		return new Can(
			SecurityUtils.can(authentication, Permission.BOARD_MANAGER),
			SecurityUtils.can(authentication, Permission.ATTENDANCE_MANAGER),
			SecurityUtils.can(authentication, Permission.SCHEDULE_MANAGER),
			SecurityUtils.can(authentication, Permission.APPROVAL_MANAGER),
			SecurityUtils.can(authentication, Permission.ROOM_MANAGER),
			SecurityUtils.can(authentication, Permission.RESOURCE_MANAGER));
	}

	/** 관리 권한. adminArea: 관리자 화면에 들어갈 수 있음 (게시판, 결재 양식, 회의실, 자원 관리가 거기 있다) */
	public record Can(boolean board, boolean attendance, boolean schedule, boolean approval, boolean room, boolean resource) {

		public boolean adminArea() {
			return board || approval || room || resource;
		}
	}
}
