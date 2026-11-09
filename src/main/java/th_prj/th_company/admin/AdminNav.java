package th_prj.th_company.admin;

import java.util.List;

/**
 * 관리자 화면 메뉴. 상단은 구분, 좌측은 지금 구분에 속한 메뉴만 보여 준다.
 *
 * @param sections 이 사용자가 쓸 수 있는 구분
 */
public record AdminNav(List<Section> sections) {

	/** @param key 컨트롤러가 model 의 menu 에 넣는 값 */
	public record Item(String key, String label, String url) {
	}

	public record Section(String label, List<Item> items) {

		/** 상단 메뉴를 누르면 첫 메뉴로 간다 */
		public String url() {
			return items.get(0).url();
		}

		public boolean contains(String menu) {
			return items.stream().anyMatch(i -> i.key().equals(menu));
		}
	}

	/** 지금 메뉴가 속한 구분. 없으면 첫 구분 */
	public Section current(String menu) {
		return sections.stream().filter(s -> s.contains(menu)).findFirst().orElse(sections.get(0));
	}
}
