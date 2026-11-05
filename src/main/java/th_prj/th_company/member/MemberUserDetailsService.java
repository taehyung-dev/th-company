package th_prj.th_company.member;

import java.util.ArrayList;
import java.util.List;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 로그인 시 member 테이블에서 사용자를 조회한다.
 * 권한: ROLE_ADMIN 또는 ROLE_USER + 관리 권한(PERM_BOARD_MANAGER 등, 본인 또는 부서에 준 권한).
 * 관리 권한을 바꾸면 해당 사용자가 다시 로그인할 때부터 적용된다.
 */
@Service
public class MemberUserDetailsService implements UserDetailsService {

	private final MemberRepository memberRepository;
	private final PermissionResolver permissionResolver;

	public MemberUserDetailsService(MemberRepository memberRepository, PermissionResolver permissionResolver) {
		this.memberRepository = memberRepository;
		this.permissionResolver = permissionResolver;
	}

	@Override
	@Transactional(readOnly = true)
	public UserDetails loadUserByUsername(String loginId) throws UsernameNotFoundException {
		Member member = memberRepository.findByLoginId(loginId)
			.orElseThrow(() -> new UsernameNotFoundException(loginId));

		List<GrantedAuthority> authorities = new ArrayList<>();
		authorities.add(new SimpleGrantedAuthority("ROLE_" + member.getRole().name()));
		for (Permission p : permissionResolver.effective(member)) {
			authorities.add(new SimpleGrantedAuthority(p.authority()));
		}

		return User.withUsername(member.getLoginId())
			.password(member.getPassword())
			.authorities(authorities)
			.disabled(!member.isEnabled())
			.build();
	}
}
