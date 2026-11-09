package th_prj.th_company.admin;

import java.util.Arrays;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;

import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import th_prj.th_company.common.BusinessException;

/**
 * 관리자 화면 상단 구분 순서. 회사 전체에 하나이고, 관리 화면마다 읽으므로 메모리에 들고 있는다.
 */
@Service
public class AdminMenuService {

	private final AdminMenuOrderRepository repository;

	private volatile List<AdminSection> cached;

	public AdminMenuService(AdminMenuOrderRepository repository) {
		this.repository = repository;
	}

	@EventListener(ApplicationReadyEvent.class)
	@Transactional
	public void removeObsoleteOrders() {
		repository.deleteRemovedSections(Arrays.stream(AdminSection.values()).map(Enum::name).toList());
		cached = null;
	}

	/** 저장되지 않은 구분(새로 추가된 구분 등)은 기본 순서대로 맨 뒤에 붙는다 */
	@Transactional(readOnly = true)
	public List<AdminSection> ordered() {
		List<AdminSection> sections = cached;
		if (sections == null) {
			Map<AdminSection, Integer> orders = new EnumMap<>(AdminSection.class);
			repository.findAll().forEach(o -> orders.put(o.getSection(), o.getSortOrder()));
			sections = Arrays.stream(AdminSection.values())
				.sorted(Comparator.comparingInt((AdminSection s) -> orders.getOrDefault(s, Integer.MAX_VALUE))
					.thenComparingInt(Enum::ordinal))
				.toList();
			cached = sections;
		}
		return sections;
	}

	/** @param sections 모든 구분을 원하는 순서대로 한 번씩 */
	@Transactional
	public void reorder(List<AdminSection> sections) {
		if (sections == null || sections.size() != AdminSection.values().length
				|| !EnumSet.copyOf(sections).equals(EnumSet.allOf(AdminSection.class))) {
			throw new BusinessException("메뉴 목록이 올바르지 않습니다. 화면을 새로고침한 뒤 다시 시도하세요.");
		}
		Map<AdminSection, AdminMenuOrder> saved = new EnumMap<>(AdminSection.class);
		repository.findAll().forEach(o -> saved.put(o.getSection(), o));
		for (int i = 0; i < sections.size(); i++) {
			AdminSection s = sections.get(i);
			int order = (i + 1) * 10;
			AdminMenuOrder o = saved.get(s);
			if (o == null) {
				repository.save(new AdminMenuOrder(s, order));
			} else {
				o.changeOrder(order);
			}
		}
		cached = List.copyOf(sections);
	}

	@Transactional
	public void reset() {
		repository.deleteAllInBatch();
		cached = List.of(AdminSection.values());
	}
}
