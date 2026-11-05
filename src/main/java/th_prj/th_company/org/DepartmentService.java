package th_prj.th_company.org;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import th_prj.th_company.common.BusinessException;
import th_prj.th_company.member.DepartmentPermissionRepository;
import th_prj.th_company.member.MemberRepository;

@Service
@Transactional(readOnly = true)
public class DepartmentService {

	private final DepartmentRepository departmentRepository;
	private final MemberRepository memberRepository;
	private final DepartmentPermissionRepository departmentPermissionRepository;

	public DepartmentService(DepartmentRepository departmentRepository, MemberRepository memberRepository,
			DepartmentPermissionRepository departmentPermissionRepository) {
		this.departmentRepository = departmentRepository;
		this.memberRepository = memberRepository;
		this.departmentPermissionRepository = departmentPermissionRepository;
	}

	/**
	 * 전체 부서를 트리 순서(부모 다음에 자식)로 펼쳐서 반환한다.
	 */
	public List<DepartmentRow> tree() {
		List<Department> all = departmentRepository.findAllByOrderBySortOrderAscNameAsc();

		Map<Long, Long> memberCounts = new HashMap<>();
		for (Object[] row : memberRepository.countGroupByDepartment()) {
			memberCounts.put((Long) row[0], (Long) row[1]);
		}

		Map<Long, List<Department>> childrenByParent = new HashMap<>();
		for (Department d : all) {
			childrenByParent.computeIfAbsent(parentIdOf(d), k -> new ArrayList<>()).add(d);
		}

		List<DepartmentRow> rows = new ArrayList<>();
		appendChildren(null, 0, childrenByParent, memberCounts, rows);
		return rows;
	}

	/** 부서와 그 하위 부서 id. 없는 부서면 빈 목록 */
	public List<Long> withDescendants(Long departmentId) {
		List<Long> ids = new ArrayList<>();
		int baseDepth = -1;
		for (DepartmentRow r : tree()) {
			if (baseDepth >= 0) {
				if (r.depth() <= baseDepth) {
					break;
				}
				ids.add(r.id());
			} else if (r.id().equals(departmentId)) {
				baseDepth = r.depth();
				ids.add(r.id());
			}
		}
		return ids;
	}

	private void appendChildren(Long parentId, int depth, Map<Long, List<Department>> childrenByParent,
			Map<Long, Long> memberCounts, List<DepartmentRow> rows) {
		for (Department d : childrenByParent.getOrDefault(parentId, List.of())) {
			rows.add(new DepartmentRow(d.getId(), d.getName(), parentId, depth, d.getSortOrder(),
				d.isEnabled(), memberCounts.getOrDefault(d.getId(), 0L)));
			appendChildren(d.getId(), depth + 1, childrenByParent, memberCounts, rows);
		}
	}

	public DepartmentForm getForm(Long id) {
		Department d = find(id);
		DepartmentForm form = new DepartmentForm();
		form.setId(d.getId());
		form.setName(d.getName());
		form.setParentId(parentIdOf(d));
		form.setSortOrder(d.getSortOrder());
		form.setEnabled(d.isEnabled());
		return form;
	}

	@Transactional
	public void save(DepartmentForm form) {
		String name = form.getName().trim();
		Department parent = form.getParentId() == null ? null : find(form.getParentId());

		if (form.getId() == null) {
			departmentRepository.save(new Department(name, parent, form.getSortOrder()));
			return;
		}

		Department d = find(form.getId());
		if (parent != null && isSelfOrDescendant(parent, d.getId())) {
			throw new BusinessException("자기 자신이나 하위 부서를 상위 부서로 지정할 수 없습니다.");
		}
		d.update(name, parent, form.getSortOrder(), form.isEnabled());
	}

	@Transactional
	public void delete(Long id) {
		Department d = find(id);
		if (departmentRepository.existsByParentId(id)) {
			throw new BusinessException("하위 부서가 있는 부서는 삭제할 수 없습니다.");
		}
		if (memberRepository.countByDepartmentId(id) > 0) {
			throw new BusinessException("소속 인원이 있는 부서는 삭제할 수 없습니다. 사용 안 함으로 변경하세요.");
		}
		// 이 부서에 준 관리 권한도 함께 지운다
		departmentPermissionRepository.deleteByDepartment(id);
		departmentRepository.delete(d);
	}

	/** candidate 에서 위로 올라가며 targetId 를 만나면 true */
	private boolean isSelfOrDescendant(Department candidate, Long targetId) {
		for (Department cur = candidate; cur != null; cur = cur.getParent()) {
			if (cur.getId().equals(targetId)) {
				return true;
			}
		}
		return false;
	}

	public Department find(Long id) {
		return departmentRepository.findById(id)
			.orElseThrow(() -> new BusinessException("부서를 찾을 수 없습니다."));
	}

	private static Long parentIdOf(Department d) {
		return d.getParent() == null ? null : d.getParent().getId();
	}
}
