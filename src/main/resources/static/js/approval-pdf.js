// 전자결재 문서 [PDF 저장]: 화면의 A4 문서(.a4-sheet)를 그대로 PDF 파일로 내려받는다.
// html2pdf.js 는 누를 때 한 번만 불러온다. 불러오지 못하면(인터넷 차단 등) 인쇄 창을 열어 "PDF로 저장"을 안내한다.
(function () {
	var button = document.getElementById('pdfButton');
	if (!button) return;
	var SRC = 'https://cdnjs.cloudflare.com/ajax/libs/html2pdf.js/0.10.1/html2pdf.bundle.min.js';

	function load() {
		return new Promise(function (resolve, reject) {
			if (window.html2pdf) { resolve(); return; }
			var script = document.createElement('script');
			script.src = SRC;
			script.onload = resolve;
			script.onerror = reject;
			document.head.appendChild(script);
		});
	}

	// 파일 이름에 쓸 수 없는 글자는 _ 로
	function fileName() {
		return (button.dataset.filename || '전자결재').replace(/[\\/:*?"<>|]+/g, '_').trim() + '.pdf';
	}

	button.addEventListener('click', function () {
		var sheet = document.querySelector('.a4-sheet');
		var label = button.textContent;
		button.disabled = true;
		button.textContent = 'PDF 만드는 중…';
		load().then(function () {
			// 화면용 그림자와 A4 최소 높이를 빼야 빈 둘째 쪽이 생기지 않는다
			sheet.classList.add('pdf-capture');
			return window.html2pdf().set({
				margin: [10, 10, 10, 10],
				filename: fileName(),
				image: { type: 'jpeg', quality: 0.95 },
				html2canvas: { scale: 2, backgroundColor: '#ffffff' },
				jsPDF: { unit: 'mm', format: 'a4', orientation: 'portrait' },
				pagebreak: { mode: ['css', 'legacy'] }
			}).from(sheet).save();
		}).catch(function () {
			alert('PDF 파일을 만들지 못했습니다. 인쇄 창에서 대상을 "PDF로 저장"으로 골라 주세요.');
			window.print();
		}).then(function () {
			sheet.classList.remove('pdf-capture');
			button.disabled = false;
			button.textContent = label;
		});
	});
})();
