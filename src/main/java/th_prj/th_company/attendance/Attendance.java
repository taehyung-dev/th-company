package th_prj.th_company.attendance;

import java.time.LocalDate;
import java.time.LocalDateTime;

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
 * 하루 근태 기록. workDate 는 오전 6시를 하루의 시작으로 본 근무일이다
 * (예: 10/25 02:00 은 10/24 근무일).
 */
@Entity
@Table(name = "attendance", uniqueConstraints = @UniqueConstraint(columnNames = {"member_id", "work_date"}))
public class Attendance {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "member_id", nullable = false)
	private Member member;

	@Column(name = "work_date", nullable = false)
	private LocalDate workDate;

	@Column(nullable = false)
	private LocalDateTime checkInAt;

	private LocalDateTime checkOutAt;

	/** 근태관리자가 정정한 경우: 정정한 사람 이름, 시각, 사유 */
	@Column(length = 50)
	private String correctedBy;

	private LocalDateTime correctedAt;

	@Column(length = 200)
	private String correctionNote;

	protected Attendance() {
	}

	public Attendance(Member member, LocalDate workDate, LocalDateTime checkInAt) {
		this.member = member;
		this.workDate = workDate;
		this.checkInAt = checkInAt;
	}

	void checkOut(LocalDateTime at) {
		this.checkOutAt = at;
	}

	void correct(LocalDateTime checkInAt, LocalDateTime checkOutAt, String by, String note, LocalDateTime at) {
		this.checkInAt = checkInAt;
		this.checkOutAt = checkOutAt;
		this.correctedBy = by;
		this.correctionNote = note;
		this.correctedAt = at;
	}

	public Long getId() { return id; }
	public Member getMember() { return member; }
	public LocalDate getWorkDate() { return workDate; }
	public LocalDateTime getCheckInAt() { return checkInAt; }
	public LocalDateTime getCheckOutAt() { return checkOutAt; }
	public String getCorrectedBy() { return correctedBy; }
	public LocalDateTime getCorrectedAt() { return correctedAt; }
	public String getCorrectionNote() { return correctionNote; }
	public boolean isCorrected() { return correctedAt != null; }
}
