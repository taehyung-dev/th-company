// 전자결재 목록: 문서를 체크하고 [결재상태 보기], [일괄결재] 를 누른다
(function () {
	var checkAll = document.getElementById('checkAll');
	if (!checkAll) return;
	var checks = Array.prototype.slice.call(document.querySelectorAll('.doc-check'));
	var count = document.getElementById('pickedCount');

	function picked() {
		return checks.filter(function (c) { return c.checked; }).map(function (c) { return c.value; });
	}

	function refresh() {
		var n = picked().length;
		count.textContent = n ? n + '건 선택' : '';
		checkAll.checked = n === checks.length;
		checkAll.indeterminate = n > 0 && n < checks.length;
	}

	checkAll.addEventListener('change', function () {
		checks.forEach(function (c) { c.checked = checkAll.checked; });
		refresh();
	});
	checks.forEach(function (c) { c.addEventListener('change', refresh); });

	function needPicked() {
		if (picked().length) return true;
		alert('문서를 먼저 체크하세요.');
		return false;
	}

	// 결재상태 보기
	var statusButton = document.getElementById('statusButton');
	var statusDialog = document.getElementById('statusDialog');
	var statusBody = document.getElementById('statusBody');
	statusButton.addEventListener('click', function () {
		if (!needPicked()) return;
		var query = picked().map(function (id) { return 'ids=' + encodeURIComponent(id); }).join('&');
		fetch(statusButton.dataset.url + '?' + query, { credentials: 'same-origin' })
			.then(function (r) {
				if (!r.ok || r.redirected) throw new Error('load failed');
				return r.text();
			})
			.then(function (html) {
				var doc = new DOMParser().parseFromString(html, 'text/html');
				statusBody.replaceChildren.apply(statusBody, Array.prototype.slice.call(doc.body.children));
				statusDialog.showModal();
			})
			.catch(function () { alert('결재상태를 불러오지 못했습니다. 화면을 새로고침한 뒤 다시 시도하세요.'); });
	});

	document.querySelectorAll('dialog [data-close]').forEach(function (b) {
		b.addEventListener('click', function () { b.closest('dialog').close(); });
	});
})();
