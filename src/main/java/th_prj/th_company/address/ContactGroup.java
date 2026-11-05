package th_prj.th_company.address;

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
 * 개인 주소록 그룹 (거래처, 협력사 등). 사용자마다 따로 가진다.
 */
@Entity
@Table(name = "contact_group", uniqueConstraints = @UniqueConstraint(columnNames = {"owner_id", "name"}))
public class ContactGroup {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "owner_id", nullable = false)
	private Member owner;

	@Column(nullable = false, length = 50)
	private String name;

	protected ContactGroup() {
	}

	public ContactGroup(Member owner, String name) {
		this.owner = owner;
		this.name = name;
	}

	public void rename(String name) {
		this.name = name;
	}

	public Long getId() { return id; }
	public Member getOwner() { return owner; }
	public String getName() { return name; }
}
