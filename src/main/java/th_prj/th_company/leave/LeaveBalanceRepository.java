package th_prj.th_company.leave;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface LeaveBalanceRepository extends JpaRepository<LeaveBalance, Long> {

	Optional<LeaveBalance> findByMemberIdAndBalanceYear(Long memberId, int balanceYear);

	List<LeaveBalance> findByBalanceYear(int balanceYear);
}
