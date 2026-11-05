package th_prj.th_company.reservation;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import th_prj.th_company.common.BusinessException;
import th_prj.th_company.reservation.ReservationViews.ResourceRow;

/**
 * 관리자 화면의 회의실 관리(회의실관리자) / 자원 관리(자원관리자). 각 화면은 자기 구분의 자원만 다룬다.
 * 예약 기록이 있는 자원은 지우지 않고 사용 안 함으로 돌린다.
 */
@Service
@Transactional(readOnly = true)
public class ResourceAdminService {

	private final ResourceRepository resourceRepository;
	private final ReservationRepository reservationRepository;

	public ResourceAdminService(ResourceRepository resourceRepository, ReservationRepository reservationRepository) {
		this.resourceRepository = resourceRepository;
		this.reservationRepository = reservationRepository;
	}

	public List<ResourceRow> rows(ReservationKind kind) {
		Map<Long, Long> counts = new HashMap<>();
		for (Object[] row : reservationRepository.countGroupByResource()) {
			counts.put((Long) row[0], (Long) row[1]);
		}
		return resourceRepository.findAllOrdered().stream()
			.filter(r -> kind.includes(r.getType()))
			.map(r -> new ResourceRow(r.getId(), r.getType(), r.getName(), r.getLocation(), r.getCapacity(),
				r.getDescription(), r.getSortOrder(), r.isEnabled(), counts.getOrDefault(r.getId(), 0L)))
			.toList();
	}

	public ResourceForm newForm(ReservationKind kind, ResourceType type) {
		ResourceForm form = new ResourceForm();
		form.setType(type != null && kind.includes(type) ? type : kind.getTypes().get(0));
		return form;
	}

	public ResourceForm getForm(ReservationKind kind, Long id) {
		Resource r = find(kind, id);
		ResourceForm form = new ResourceForm();
		form.setId(r.getId());
		form.setType(r.getType());
		form.setName(r.getName());
		form.setLocation(r.getLocation());
		form.setCapacity(r.getCapacity());
		form.setDescription(r.getDescription());
		form.setSortOrder(r.getSortOrder());
		form.setEnabled(r.isEnabled());
		return form;
	}

	@Transactional
	public Long save(ReservationKind kind, ResourceForm form) {
		if (!kind.includes(form.getType())) {
			throw new BusinessException("종류를 다시 선택하세요.");
		}
		String name = form.getName().trim();
		boolean duplicate = resourceRepository.findAll().stream()
			.anyMatch(r -> r.getName().equalsIgnoreCase(name) && !r.getId().equals(form.getId()));
		if (duplicate) {
			throw new BusinessException("같은 이름의 회의실이나 자원이 이미 있습니다.");
		}
		Resource r = form.getId() == null
			? new Resource(form.getType(), name, null, null, null, 0)
			: find(kind, form.getId());
		r.update(form.getType(), name, blankToNull(form.getLocation()), form.getCapacity(),
			blankToNull(form.getDescription()), form.getSortOrder(), form.getId() == null || form.isEnabled());
		return resourceRepository.save(r).getId();
	}

	/** 예약 기록이 없을 때만 삭제 */
	@Transactional
	public void delete(ReservationKind kind, Long id) {
		Resource r = find(kind, id);
		long count = reservationRepository.countByResourceId(id);
		if (count > 0) {
			throw new BusinessException("'" + r.getName() + "'은(는) 예약 기록이 " + count
				+ "건 있어 삭제할 수 없습니다. 수정에서 '사용'을 해제하세요.");
		}
		resourceRepository.delete(r);
	}

	/** 다른 구분의 자원은 없는 것으로 (회의실관리자가 자원을 고치지 못하게) */
	private Resource find(ReservationKind kind, Long id) {
		return resourceRepository.findById(id)
			.filter(r -> kind.includes(r.getType()))
			.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
	}

	private static String blankToNull(String s) {
		return s == null || s.isBlank() ? null : s.trim();
	}
}
