package th_prj.th_company.menu;

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
 * 상단 메뉴 순서. 모든 화면이 읽으므로 메모리에 들고 있다가 순서를 바꿀 때만 다시 읽는다.
 */
@Service
public class MenuService {

	private final MenuOrderRepository repository;

	private volatile List<MainMenu> cached;

	public MenuService(MenuOrderRepository repository) {
		this.repository = repository;
	}

	/** 예전에 메뉴였던 항목(조직도 등)의 순서가 DB 에 남아 있으면 시작할 때 지운다 */
	@EventListener(ApplicationReadyEvent.class)
	@Transactional
	public void removeObsoleteOrders() {
		repository.deleteRemovedMenus(Arrays.stream(MainMenu.values()).map(Enum::name).toList());
		cached = null;
	}

	/** 화면에 보일 순서. 저장되지 않은 메뉴(새로 추가된 메뉴 등)는 기본 순서대로 맨 뒤에 붙는다 */
	@Transactional(readOnly = true)
	public List<MainMenu> ordered() {
		List<MainMenu> menus = cached;
		if (menus == null) {
			Map<MainMenu, Integer> orders = new EnumMap<>(MainMenu.class);
			repository.findAll().forEach(o -> orders.put(o.getMenu(), o.getSortOrder()));
			menus = Arrays.stream(MainMenu.values())
				.sorted(Comparator.comparingInt((MainMenu m) -> orders.getOrDefault(m, Integer.MAX_VALUE))
					.thenComparingInt(Enum::ordinal))
				.toList();
			cached = menus;
		}
		return menus;
	}

	/** @param menus 모든 메뉴를 원하는 순서대로 한 번씩 */
	@Transactional
	public void reorder(List<MainMenu> menus) {
		if (menus == null || menus.size() != MainMenu.values().length
				|| !EnumSet.copyOf(menus).equals(EnumSet.allOf(MainMenu.class))) {
			throw new BusinessException("메뉴 목록이 올바르지 않습니다. 화면을 새로고침한 뒤 다시 시도하세요.");
		}
		Map<MainMenu, MenuOrder> saved = new EnumMap<>(MainMenu.class);
		repository.findAll().forEach(o -> saved.put(o.getMenu(), o));
		for (int i = 0; i < menus.size(); i++) {
			MainMenu m = menus.get(i);
			int order = (i + 1) * 10;
			MenuOrder o = saved.get(m);
			if (o == null) {
				repository.save(new MenuOrder(m, order));
			} else {
				o.changeOrder(order);
			}
		}
		cached = List.copyOf(menus);
	}

	/** 기본 순서로 되돌린다 */
	@Transactional
	public void reset() {
		repository.deleteAllInBatch();
		cached = List.of(MainMenu.values());
	}
}
