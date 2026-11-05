package th_prj.th_company.reservation;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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

/**
 * 자원 예약. 같은 자원의 예약끼리는 시간이 겹칠 수 없다 ([startAt, endAt) 구간).
 */
@Entity
@Table(name = "reservation", indexes = @Index(name = "idx_reservation_resource_period", columnList = "resource_id, startAt, endAt"))
public class Reservation {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "resource_id", nullable = false)
	private Resource resource;

	/** 예약자 */
	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "owner_id", nullable = false)
	private Member owner;

	@Column(nullable = false, length = 100)
	private String title;

	@Column(nullable = false)
	private LocalDateTime startAt;

	@Column(nullable = false)
	private LocalDateTime endAt;

	@Column(length = 500)
	private String memo;

	/** 반복 예약이면 그 묶음 */
	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "series_id")
	private ReservationSeries series;

	@Column(nullable = false, updatable = false)
	private LocalDateTime createdAt;

	@Column(nullable = false)
	private LocalDateTime updatedAt;

	protected Reservation() {
	}

	public Reservation(Member owner) {
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

	public void update(Resource resource, String title, LocalDateTime startAt, LocalDateTime endAt, String memo) {
		this.resource = resource;
		this.title = title;
		this.startAt = startAt;
		this.endAt = endAt;
		this.memo = memo;
	}

	void joinSeries(ReservationSeries series) {
		this.series = series;
	}

	public ReservationSeries getSeries() { return series; }
	public Long getId() { return id; }
	public Resource getResource() { return resource; }
	public Member getOwner() { return owner; }
	public String getTitle() { return title; }
	public LocalDateTime getStartAt() { return startAt; }
	public LocalDateTime getEndAt() { return endAt; }
	public String getMemo() { return memo; }
	public LocalDateTime getCreatedAt() { return createdAt; }
}
