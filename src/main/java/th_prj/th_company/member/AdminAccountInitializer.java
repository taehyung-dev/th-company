package th_prj.th_company.member;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 기동 시 sysadmin 계정이 없으면 생성한다 (초기 비밀번호: a).
 * 운영 배포 전 비밀번호 변경 기능을 만들고 초기 비밀번호를 바꿔야 한다.
 */
@Component
public class AdminAccountInitializer implements ApplicationRunner {

	private static final Logger log = LoggerFactory.getLogger(AdminAccountInitializer.class);

	private static final String ADMIN_LOGIN_ID = "sysadmin";
	private static final String ADMIN_INITIAL_PASSWORD = "a";

	private final MemberRepository memberRepository;
	private final PasswordEncoder passwordEncoder;

	public AdminAccountInitializer(MemberRepository memberRepository, PasswordEncoder passwordEncoder) {
		this.memberRepository = memberRepository;
		this.passwordEncoder = passwordEncoder;
	}

	@Override
	@Transactional
	public void run(ApplicationArguments args) {
		if (memberRepository.existsByLoginId(ADMIN_LOGIN_ID)) {
			return;
		}
		memberRepository.save(new Member(ADMIN_LOGIN_ID,
			passwordEncoder.encode(ADMIN_INITIAL_PASSWORD), "시스템관리자", Role.ADMIN));
		log.info("관리자 계정({})을 생성했습니다.", ADMIN_LOGIN_ID);
	}
}
