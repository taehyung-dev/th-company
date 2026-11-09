package th_prj.th_company.address;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import th_prj.th_company.address.AddressViews.ContactRow;
import th_prj.th_company.address.AddressViews.DeptOption;
import th_prj.th_company.address.AddressViews.Employee;
import th_prj.th_company.address.AddressViews.GroupItem;
import th_prj.th_company.address.AddressViews.PersonalNav;
import th_prj.th_company.common.Address;
import th_prj.th_company.common.BusinessException;
import th_prj.th_company.member.Member;
import th_prj.th_company.member.MemberRepository;
import th_prj.th_company.org.DepartmentService;
import th_prj.th_company.org.OrgChartService;

/**
 * 주소록. 사내 주소록은 재직 중인 임직원(읽기 전용), 개인 주소록은 사용자마다 따로 관리하는 연락처.
 */
@Service
@Transactional(readOnly = true)
public class AddressService {

	public static final int PAGE_SIZE = 20;

	private final MemberRepository memberRepository;
	private final DepartmentService departmentService;
	private final ContactRepository contactRepository;
	private final ContactGroupRepository groupRepository;

	public AddressService(MemberRepository memberRepository, DepartmentService departmentService,
			ContactRepository contactRepository, ContactGroupRepository groupRepository) {
		this.memberRepository = memberRepository;
		this.departmentService = departmentService;
		this.contactRepository = contactRepository;
		this.groupRepository = groupRepository;
	}

	// ---- 사내 주소록 ----

	/** 사용 중인 부서 (트리 순서) */
	public List<DeptOption> departments() {
		return OrgChartService.visibleRows(departmentService.tree()).stream()
			.map(r -> new DeptOption(r.id(), r.name(), r.depth()))
			.toList();
	}

	/**
	 * @param departmentId 지정하면 그 부서와 하위 부서 인원만
	 * @param page         0부터
	 */
	public Page<Employee> employees(String keyword, Long departmentId, int page) {
		List<Long> deptIds = departmentId == null ? List.of(-1L) : withSubDepartments(departmentId);
		return memberRepository.searchDirectory(departmentId == null, deptIds, blankToNull(keyword),
				PageRequest.of(Math.max(page, 0), PAGE_SIZE))
			.map(AddressService::toEmployee);
	}

	/**
	 * 사내 주소록을 조직도 형식으로: 재직자 전체(검색어가 있으면 회사 전체에서 찾은 사람). 직책 → 직급 → 이름 순.
	 */
	public List<Employee> directory(String keyword) {
		return memberRepository.searchDirectory(true, List.of(-1L), blankToNull(keyword), Pageable.unpaged()).stream()
			.sorted(OrgChartService.MEMBER_ORDER)
			.map(AddressService::toEmployee)
			.toList();
	}

	private List<Long> withSubDepartments(Long departmentId) {
		List<Long> ids = departmentService.withDescendants(departmentId);
		return ids.isEmpty() ? List.of(-1L) : ids;
	}

	private static Employee toEmployee(Member m) {
		return new Employee(m.getId(), m.getName(),
			m.getGrade() == null ? null : m.getGrade().getName(),
			m.getDuty() == null ? null : m.getDuty().getName(),
			m.getDepartment() == null ? null : m.getDepartment().getName(),
			m.getEmail(), m.getPhone(),
			m.getDepartment() == null ? null : m.getDepartment().getId(), m.getPhotoUrl());
	}

	// ---- 개인 주소록 ----

	public PersonalNav personalNav(String loginId) {
		Long ownerId = owner(loginId).getId();
		Map<Long, Long> counts = contactRepository.countByGroup(ownerId).stream()
			.collect(Collectors.toMap(r -> (Long) r[0], r -> (Long) r[1]));
		List<GroupItem> groups = groupRepository.findByOwnerIdOrderByNameAsc(ownerId).stream()
			.map(g -> new GroupItem(g.getId(), g.getName(), counts.getOrDefault(g.getId(), 0L)))
			.toList();
		return new PersonalNav(contactRepository.countByOwnerId(ownerId),
			contactRepository.countByOwnerIdAndFavoriteTrue(ownerId), groups);
	}

	public Page<ContactRow> contacts(String loginId, boolean favoriteOnly, Long groupId, String keyword, int page) {
		return contactRepository.search(owner(loginId).getId(), favoriteOnly, groupId, blankToNull(keyword),
				PageRequest.of(Math.max(page, 0), PAGE_SIZE))
			.map(AddressService::toRow);
	}

	public ContactForm newForm(Long groupId) {
		ContactForm form = new ContactForm();
		form.setGroupId(groupId);
		return form;
	}

	public ContactForm getForm(Long id, String loginId) {
		Contact c = ownedContact(id, loginId);
		ContactForm form = new ContactForm();
		form.setId(c.getId());
		form.setGroupId(c.getGroup() == null ? null : c.getGroup().getId());
		form.setName(c.getName());
		form.setCompany(c.getCompany());
		form.setDepartment(c.getDepartment());
		form.setPosition(c.getPosition());
		form.setMobile(c.getMobile());
		form.setPhone(c.getPhone());
		form.setEmail(c.getEmail());
		form.setAddress(c.getAddress());
		form.setMemo(c.getMemo());
		form.setFavorite(c.isFavorite());
		return form;
	}

	@Transactional
	public Long save(ContactForm form, String loginId) {
		Member owner = owner(loginId);
		Contact c = form.getId() == null ? new Contact(owner) : ownedContact(form.getId(), loginId);
		ContactGroup group = form.getGroupId() == null ? null : ownedGroup(form.getGroupId(), loginId);
		c.update(group, form.getName().trim(), trim(form.getCompany()), trim(form.getDepartment()),
			trim(form.getPosition()), trim(form.getMobile()), trim(form.getPhone()), trim(form.getEmail()),
			form.toAddress(), trim(form.getMemo()), form.isFavorite());
		return contactRepository.save(c).getId();
	}

	@Transactional
	public String delete(Long id, String loginId) {
		Contact c = ownedContact(id, loginId);
		contactRepository.delete(c);
		return c.getName();
	}

	@Transactional
	public boolean toggleFavorite(Long id, String loginId) {
		Contact c = ownedContact(id, loginId);
		c.toggleFavorite();
		return c.isFavorite();
	}

	/** 사내 주소록의 임직원을 내 개인 주소록에 복사한다 */
	@Transactional
	public String addEmployee(Long memberId, String loginId) {
		Member owner = owner(loginId);
		Member m = memberRepository.findById(memberId)
			.filter(Member::isEnabled)
			.orElseThrow(() -> new BusinessException("사내 주소록에 없는 사용자입니다."));
		if (m.getId().equals(owner.getId())) {
			throw new BusinessException("본인은 개인 주소록에 추가할 수 없습니다.");
		}
		if (contactRepository.existsByOwnerIdAndNameAndEmail(owner.getId(), m.getName(), m.getEmail())) {
			throw new BusinessException(m.getName() + "님은 이미 개인 주소록에 있습니다.");
		}
		Contact c = new Contact(owner);
		c.update(null, m.getName(), "TH Company",
			m.getDepartment() == null ? null : m.getDepartment().getName(),
			m.getGrade() == null ? null : m.getGrade().getName(),
			m.getPhone(), null, m.getEmail(), null, null, false);
		contactRepository.save(c);
		return m.getName();
	}

	/** 개인 주소록 전체를 CSV 로 (엑셀에서 한글이 깨지지 않게 BOM 을 붙인다) */
	public String exportCsv(String loginId) {
		StringBuilder sb = new StringBuilder("﻿");
		sb.append("이름,회사,부서,직위,휴대폰,전화,이메일,우편번호,도로명주소,지번주소,상세주소,그룹,즐겨찾기,메모\r\n");
		for (Contact c : contactRepository.findAllByOwner(owner(loginId).getId())) {
			Address a = c.getAddress();
			List<String> cols = List.of(nz(c.getName()), nz(c.getCompany()), nz(c.getDepartment()),
				nz(c.getPosition()), nz(c.getMobile()), nz(c.getPhone()), nz(c.getEmail()),
				a == null ? "" : nz(a.getZipCode()), a == null ? "" : nz(a.getRoadAddress()),
				a == null ? "" : nz(a.getJibunAddress()), a == null ? "" : nz(a.getDetail()),
				c.getGroup() == null ? "" : c.getGroup().getName(), c.isFavorite() ? "Y" : "", nz(c.getMemo()));
			sb.append(cols.stream().map(AddressService::csv).collect(Collectors.joining(","))).append("\r\n");
		}
		return sb.toString();
	}

	// ---- 그룹 ----

	@Transactional
	public void createGroup(String name, String loginId) {
		Member owner = owner(loginId);
		String n = groupName(name);
		if (groupRepository.existsByOwnerIdAndName(owner.getId(), n)) {
			throw new BusinessException("'" + n + "' 그룹이 이미 있습니다.");
		}
		groupRepository.save(new ContactGroup(owner, n));
	}

	@Transactional
	public void renameGroup(Long id, String name, String loginId) {
		ContactGroup g = ownedGroup(id, loginId);
		String n = groupName(name);
		if (groupRepository.existsByOwnerIdAndNameAndIdNot(g.getOwner().getId(), n, id)) {
			throw new BusinessException("'" + n + "' 그룹이 이미 있습니다.");
		}
		g.rename(n);
	}

	/** 그룹만 지우고 소속 연락처는 그룹 없음으로 남긴다 */
	@Transactional
	public String deleteGroup(Long id, String loginId) {
		ContactGroup g = ownedGroup(id, loginId);
		contactRepository.clearGroup(id);
		groupRepository.delete(g);
		return g.getName();
	}

	// ---- 내부 ----

	private Member owner(String loginId) {
		return memberRepository.findByLoginId(loginId)
			.orElseThrow(() -> new AccessDeniedException("사용자를 찾을 수 없습니다."));
	}

	private Contact ownedContact(Long id, String loginId) {
		return contactRepository.findById(id)
			.filter(c -> c.getOwner().getLoginId().equals(loginId))
			.orElseThrow(() -> new AccessDeniedException("내 주소록의 연락처가 아닙니다."));
	}

	private ContactGroup ownedGroup(Long id, String loginId) {
		return groupRepository.findById(id)
			.filter(g -> g.getOwner().getLoginId().equals(loginId))
			.orElseThrow(() -> new BusinessException("그룹을 찾을 수 없습니다."));
	}

	private static String groupName(String name) {
		String n = name == null ? "" : name.trim();
		if (n.isEmpty()) {
			throw new BusinessException("그룹 이름을 입력하세요.");
		}
		if (n.length() > 50) {
			throw new BusinessException("그룹 이름은 50자 이하로 입력하세요.");
		}
		return n;
	}

	private static ContactRow toRow(Contact c) {
		return new ContactRow(c.getId(), c.getName(), c.getCompany(), c.getDepartment(), c.getPosition(),
			c.getMobile(), c.getPhone(), c.getEmail(), c.getGroup() == null ? null : c.getGroup().getName(),
			c.isFavorite());
	}

	private static String csv(String s) {
		// 엑셀이 수식으로 해석하지 않도록 =,+,-,@ 로 시작하면 앞에 ' 를 붙인다
		String v = !s.isEmpty() && "=+-@".indexOf(s.charAt(0)) >= 0 ? "'" + s : s;
		return v.contains(",") || v.contains("\"") || v.contains("\n") || v.contains("\r")
			? "\"" + v.replace("\"", "\"\"") + "\"" : v;
	}

	private static String nz(String s) {
		return s == null ? "" : s;
	}

	private static String trim(String s) {
		return s == null || s.isBlank() ? null : s.trim();
	}

	private static String blankToNull(String s) {
		return s == null || s.isBlank() ? null : s.trim();
	}
}
