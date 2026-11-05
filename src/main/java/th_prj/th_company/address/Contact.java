package th_prj.th_company.address;

import java.time.LocalDateTime;

import jakarta.persistence.AttributeOverride;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
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
import th_prj.th_company.common.Address;
import th_prj.th_company.member.Member;

/**
 * 개인 주소록 연락처. owner 본인만 보고 고친다.
 */
@Entity
@Table(name = "contact", indexes = @Index(name = "idx_contact_owner", columnList = "owner_id, name"))
public class Contact {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "owner_id", nullable = false)
	private Member owner;

	/** 그룹 (없어도 됨) */
	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "group_id")
	private ContactGroup group;

	@Column(nullable = false, length = 50)
	private String name;

	@Column(length = 100)
	private String company;

	@Column(length = 100)
	private String department;

	/** 직위 */
	@Column(length = 50)
	private String position;

	@Column(length = 30)
	private String mobile;

	@Column(length = 30)
	private String phone;

	@Column(length = 100)
	private String email;

	/** 상세주소는 예전 단일 주소 컬럼(address)을 그대로 쓴다. 기존에 입력한 주소는 상세주소로 보인다 */
	@Embedded
	@AttributeOverride(name = "detail", column = @Column(name = "address", length = 200))
	private Address address;

	@Column(length = 1000)
	private String memo;

	@Column(nullable = false)
	private boolean favorite;

	@Column(nullable = false, updatable = false)
	private LocalDateTime createdAt;

	@Column(nullable = false)
	private LocalDateTime updatedAt;

	protected Contact() {
	}

	public Contact(Member owner) {
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

	public void update(ContactGroup group, String name, String company, String department, String position,
			String mobile, String phone, String email, Address address, String memo, boolean favorite) {
		this.group = group;
		this.name = name;
		this.company = company;
		this.department = department;
		this.position = position;
		this.mobile = mobile;
		this.phone = phone;
		this.email = email;
		this.address = address;
		this.memo = memo;
		this.favorite = favorite;
	}

	public void toggleFavorite() {
		favorite = !favorite;
	}

	public Long getId() { return id; }
	public Member getOwner() { return owner; }
	public ContactGroup getGroup() { return group; }
	public String getName() { return name; }
	public String getCompany() { return company; }
	public String getDepartment() { return department; }
	public String getPosition() { return position; }
	public String getMobile() { return mobile; }
	public String getPhone() { return phone; }
	public String getEmail() { return email; }
	public Address getAddress() { return address; }
	public String getMemo() { return memo; }
	public boolean isFavorite() { return favorite; }
}
