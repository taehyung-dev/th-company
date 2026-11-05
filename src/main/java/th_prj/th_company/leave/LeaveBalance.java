package th_prj.th_company.leave;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import th_prj.th_company.member.Member;

/**
 * 근태관리자가 직접 정한 그해 연차 일수. 행이 없으면 입사일로 계산한 값을 쓴다.
 */
@Entity
@Table(name = "leave_balance", uniqueConstraints = @UniqueConstraint(columnNames = {"member_id", "balanceYear"}))
public class LeaveBalance {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "member_id", nullable = false)
	private Member member;

	@Column(nullable = false)
	private int balanceYear;

	@Column(nullable = false)
	private double grantedDays;

	protected LeaveBalance() {
	}

	public LeaveBalance(Member member, int balanceYear, double grantedDays) {
		this.member = member;
		this.balanceYear = balanceYear;
		this.grantedDays = grantedDays;
	}

	void changeGranted(double days) {
		this.grantedDays = days;
	}

	public Member getMember() { return member; }
	public int getBalanceYear() { return balanceYear; }
	public double getGrantedDays() { return grantedDays; }
}
