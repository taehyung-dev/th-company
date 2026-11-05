package th_prj.th_company.attendance;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.authentication.event.AuthenticationSuccessEvent;
import org.springframework.stereotype.Component;

/**
 * 로그인 성공 시 출근을 기록한다. 기록에 실패해도 로그인은 막지 않는다.
 */
@Component
public class AttendanceLoginListener {

	private static final Logger log = LoggerFactory.getLogger(AttendanceLoginListener.class);

	private final AttendanceService attendanceService;

	public AttendanceLoginListener(AttendanceService attendanceService) {
		this.attendanceService = attendanceService;
	}

	@EventListener
	public void onLogin(AuthenticationSuccessEvent event) {
		String loginId = event.getAuthentication().getName();
		try {
			if (attendanceService.recordLogin(loginId)) {
				log.info("출근 기록: {}", loginId);
			}
		} catch (DataIntegrityViolationException e) {
			// 같은 사용자가 동시에 두 번 로그인한 경우 — 이미 기록됨
			log.debug("출근 중복 기록 무시: {}", loginId);
		} catch (RuntimeException e) {
			log.warn("출근 기록 실패: {}", loginId, e);
		}
	}
}
