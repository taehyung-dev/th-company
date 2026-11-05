package th_prj.th_company.notification;

import java.time.LocalDateTime;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

	@Query("select count(n) from Notification n where n.recipient.loginId = :loginId and n.readAt is null")
	long countUnread(@Param("loginId") String loginId);

	@Query("select n from Notification n where n.recipient.loginId = :loginId order by n.id desc")
	Page<Notification> findMine(@Param("loginId") String loginId, Pageable pageable);

	@Modifying
	@Query("""
		update Notification n set n.readAt = :now
		where n.readAt is null and n.recipient.id in (select m.id from Member m where m.loginId = :loginId)
		""")
	int markAllRead(@Param("loginId") String loginId, @Param("now") LocalDateTime now);
}
