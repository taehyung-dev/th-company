package th_prj.th_company.common;

import org.springframework.security.core.Authentication;

import th_prj.th_company.member.Permission;

public final class SecurityUtils {

	private SecurityUtils() {
	}

	public static boolean isAdmin(Authentication authentication) {
		return hasAuthority(authentication, "ROLE_ADMIN");
	}

	/** 관리자(ADMIN)는 모든 관리 권한을 가진 것으로 본다 */
	public static boolean can(Authentication authentication, Permission permission) {
		return isAdmin(authentication) || hasAuthority(authentication, permission.authority());
	}

	private static boolean hasAuthority(Authentication authentication, String authority) {
		return authentication != null && authentication.getAuthorities().stream()
			.anyMatch(a -> a.getAuthority().equals(authority));
	}
}
