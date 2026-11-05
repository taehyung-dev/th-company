package th_prj.th_company.common;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 주소 입력칸을 가진 폼의 공통 부분. 화면은 fragments/address :: fields 를 쓴다.
 */
public abstract class AddressForm {

	@Pattern(regexp = "\\d{5}|", message = "우편번호는 숫자 5자리입니다. [주소 검색]으로 입력하세요.")
	private String zipCode;

	@Size(max = 200, message = "도로명주소는 200자 이하입니다.")
	private String roadAddress;

	@Size(max = 200, message = "지번주소는 200자 이하입니다.")
	private String jibunAddress;

	@Size(max = 200, message = "상세주소는 200자 이하로 입력하세요.")
	private String addressDetail;

	/** 입력값을 주소로 (모두 비었으면 null) */
	public Address toAddress() {
		return Address.of(zipCode, roadAddress, jibunAddress, addressDetail);
	}

	public void setAddress(Address address) {
		zipCode = address == null ? null : address.getZipCode();
		roadAddress = address == null ? null : address.getRoadAddress();
		jibunAddress = address == null ? null : address.getJibunAddress();
		addressDetail = address == null ? null : address.getDetail();
	}

	public String getZipCode() { return zipCode; }
	public void setZipCode(String zipCode) { this.zipCode = zipCode; }
	public String getRoadAddress() { return roadAddress; }
	public void setRoadAddress(String roadAddress) { this.roadAddress = roadAddress; }
	public String getJibunAddress() { return jibunAddress; }
	public void setJibunAddress(String jibunAddress) { this.jibunAddress = jibunAddress; }
	public String getAddressDetail() { return addressDetail; }
	public void setAddressDetail(String addressDetail) { this.addressDetail = addressDetail; }
}
