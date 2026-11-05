package th_prj.th_company.reservation;

import java.util.List;

import th_prj.th_company.member.Permission;

/**
 * 예약 메뉴 구분. 회의실 예약과 자원(비품·장비, 차량) 예약은 메뉴, 관리 화면, 관리 권한이 따로다.
 * path 는 URL 용 (/reservations/rooms, /admin/rooms), menuKey 는 상단 메뉴(MainMenu.key)와 같다.
 */
public enum ReservationKind {
	ROOM("rooms", "회의실", "room", Permission.ROOM_MANAGER, List.of(ResourceType.ROOM)),
	RESOURCE("resources", "자원", "reservation", Permission.RESOURCE_MANAGER,
		List.of(ResourceType.EQUIPMENT, ResourceType.VEHICLE));

	private final String path;
	private final String label;
	private final String menuKey;
	private final Permission permission;
	private final List<ResourceType> types;

	ReservationKind(String path, String label, String menuKey, Permission permission, List<ResourceType> types) {
		this.path = path;
		this.label = label;
		this.menuKey = menuKey;
		this.permission = permission;
		this.types = types;
	}

	public static ReservationKind of(ResourceType type) {
		for (ReservationKind k : values()) {
			if (k.types.contains(type)) {
				return k;
			}
		}
		throw new IllegalArgumentException(String.valueOf(type));
	}

	public static ReservationKind of(Resource resource) {
		return of(resource.getType());
	}

	/** rooms → ROOM. 없는 값이면 null */
	public static ReservationKind fromPath(String path) {
		for (ReservationKind k : values()) {
			if (k.path.equals(path)) {
				return k;
			}
		}
		return null;
	}

	public boolean includes(ResourceType type) {
		return types.contains(type);
	}

	/** 종류가 여럿이라 화면에서 종류를 고르게 할지 */
	public boolean hasSubTypes() {
		return types.size() > 1;
	}

	public String getPath() { return path; }
	public String getLabel() { return label; }
	public String getMenuKey() { return menuKey; }
	public Permission getPermission() { return permission; }
	public List<ResourceType> getTypes() { return types; }
}
