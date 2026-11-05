package th_prj.th_company.schedule;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import th_prj.th_company.member.Member;
import th_prj.th_company.org.Department;

/**
 * 일정. 회사일정은 전원, 부서일정은 department 소속원, 개인일정은 owner 만 본다.
 * 종일 일정은 startAt = 시작일 00:00, endAt = 종료일 23:59 로 저장한다.
 */
@Entity
@Table(name = "schedule", indexes = @Index(name = "idx_schedule_period", columnList = "startAt, endAt"))
public class Schedule {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private ScheduleType type;

	@Column(nullable = false, length = 200)
	private String title;

	@Column(length = 2000)
	private String description;

	@Column(nullable = false)
	private LocalDateTime startAt;

	@Column(nullable = false)
	private LocalDateTime endAt;

	@Column(nullable = false)
	private boolean allDay;

	/** 등록자 */
	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "owner_id", nullable = false)
	private Member owner;

	/** 부서일정의 대상 부서 (등록자의 부서) */
	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "department_id")
	private Department department;

	@Column(nullable = false, updatable = false)
	private LocalDateTime createdAt;

	@Column(nullable = false)
	private LocalDateTime updatedAt;

	protected Schedule() {
	}

	public Schedule(Member owner) {
		this.owner = owner;
	}

	@PrePersist
	void onCreate() {
		createdAt = updatedAt = LocalDateTime.now();
	}

	@PreUpdate
	void onUpdate() {
		updatedAt = LocalDateTime.now();
	}

	public void update(ScheduleType type, Department department, String title, String description,
			LocalDateTime startAt, LocalDateTime endAt, boolean allDay) {
		this.type = type;
		this.department = department;
		this.title = title;
		this.description = description;
		this.startAt = startAt;
		this.endAt = endAt;
		this.allDay = allDay;
	}

	public Long getId() { return id; }
	public ScheduleType getType() { return type; }
	public String getTitle() { return title; }
	public String getDescription() { return description; }
	public LocalDateTime getStartAt() { return startAt; }
	public LocalDateTime getEndAt() { return endAt; }
	public boolean isAllDay() { return allDay; }
	public Member getOwner() { return owner; }
	public Department getDepartment() { return department; }
}
