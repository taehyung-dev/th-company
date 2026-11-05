// 결재선·참조자 지정 팝업 (approval/fragments :: picker).
// [data-picker] 버튼마다 대상 목록이 다르다: data-list(목록 id), data-empty(안내 id), data-input(hidden 이름),
// data-max, data-title, data-ordered(결재 순서가 있는지), data-stamp(결재란 표 id, 선택),
// data-pane(오른쪽 목록 이름, 선택), data-tag(사람 앞에 붙는 표시, 선택).
// 열 때 대상 목록을 복사해 오고, [확인]하면 대상 목록을 다시 만든다. [취소]는 변경을 버린다.
// 대상 목록과 hidden 값은 결재 순서(1차가 처음)이고, 팝업의 오른쪽 목록만 높은 사람이 위로 가도록 거꾸로 보여 준다.

// 결재란 표의 결재자 칸을 목록(결재 순서) 대로 다시 만든다. 값은 textContent 로만 넣는다
function renderLineStamp(table, list) {
	var grades = table.rows[0], names = table.rows[1], dates = table.rows[2];
	while (grades.cells.length > 2) grades.deleteCell(-1);
	while (names.cells.length > 1) names.deleteCell(-1);
	while (dates.cells.length > 1) dates.deleteCell(-1);
	Array.prototype.forEach.call(list.children, function (li, i) {
		var grade = grades.insertCell(-1);
		grade.className = 'stamp-grade';
		grade.textContent = li.dataset.grade || (i + 1) + '차';
		var box = names.insertCell(-1);
		box.className = 'stamp-box';
		var name = document.createElement('span');
		name.className = 'stamp-name';
		name.textContent = li.dataset.name;
		box.appendChild(name);
		dates.insertCell(-1).className = 'stamp-date';
	});
}

(function () {
	var dialog = document.getElementById('linePicker');
	if (!dialog) return;
	var org = document.getElementById('orgPick');
	var line = document.getElementById('pickLine');
	var search = document.getElementById('orgSearch');
	var title = document.getElementById('pickTitle');
	var paneTitle = document.getElementById('pickPaneTitle');
	var order = document.getElementById('pickOrder');
	var target = null;        // 지금 고르는 대상 { list, empty, input, max, label, ordered }
	var orgSelected = null;   // 조직도에서 선택한 사람 li
	var lineSelected = null;  // 오른쪽 목록에서 선택한 li
	var dragged = null;       // 끌고 있는 li (조직도의 사람 또는 오른쪽 목록의 사람)

	function person(el) {
		return { id: el.dataset.id, name: el.dataset.name, grade: el.dataset.grade || '', label: el.dataset.label };
	}

	function refreshEmpty(list, empty) {
		empty.style.display = list.children.length ? 'none' : '';
	}

	// ----- 조직도 -----

	// 접힌 부서의 하위 줄과 검색에 맞지 않는 사람을 숨긴다
	function refreshOrg() {
		var q = search.value.trim();
		var rows = org.children;
		var collapsedDepth = Infinity;
		for (var i = 0; i < rows.length; i++) {
			var row = rows[i];
			var depth = parseInt(row.dataset.depth, 10);
			if (depth <= collapsedDepth) collapsedDepth = Infinity;
			var hiddenByFold = depth > collapsedDepth;
			if (row.classList.contains('pick-dept')) {
				row.hidden = hiddenByFold && !q;
				if (!q && row.classList.contains('collapsed') && collapsedDepth === Infinity) collapsedDepth = depth;
			} else {
				var match = !q || row.dataset.name.indexOf(q) >= 0;
				row.hidden = q ? !match : hiddenByFold;
				row.classList.toggle('picked', !!line.querySelector('[data-id="' + row.dataset.id + '"]'));
			}
		}
		// 검색 중에는 맞는 사람이 없는 부서 줄을 숨긴다
		if (q) {
			for (var j = 0; j < rows.length; j++) {
				if (!rows[j].classList.contains('pick-dept')) continue;
				var has = false;
				for (var k = j + 1; k < rows.length && !rows[k].classList.contains('pick-dept'); k++) {
					if (!rows[k].hidden) { has = true; break; }
				}
				rows[j].hidden = !has;
			}
		}
	}

	org.addEventListener('click', function (e) {
		var toggle = e.target.closest('.pick-toggle');
		if (toggle) {
			var dept = toggle.closest('.pick-dept');
			dept.classList.toggle('collapsed');
			toggle.textContent = dept.classList.contains('collapsed') ? '▸' : '▾';
			refreshOrg();
			return;
		}
		var member = e.target.closest('.pick-member');
		if (member) selectOrg(member);
	});
	org.addEventListener('dblclick', function (e) {
		var member = e.target.closest('.pick-member');
		if (member) { selectOrg(member); add(); }
	});
	org.addEventListener('keydown', function (e) {
		if (e.key === 'Enter' && e.target.classList.contains('pick-member')) { selectOrg(e.target); add(); }
	});
	search.addEventListener('input', refreshOrg);

	function selectOrg(el) {
		if (orgSelected) orgSelected.classList.remove('selected');
		orgSelected = el;
		el.classList.add('selected');
	}

	// ----- 오른쪽 목록 -----

	function lineItem(p) {
		var li = document.createElement('li');
		li.dataset.id = p.id;
		li.dataset.name = p.name;
		li.dataset.grade = p.grade;
		li.dataset.label = p.label;
		li.draggable = true;
		li.textContent = p.label;
		var remove = document.createElement('button');
		remove.type = 'button';
		remove.className = 'btn-link danger';
		remove.title = '빼기';
		remove.textContent = '✕';
		li.appendChild(remove);
		return li;
	}

	function refreshLine() {
		document.getElementById('pickCount').textContent = line.children.length + '명 / 최대 ' + target.max + '명';
		document.getElementById('pickEmpty').style.display = line.children.length ? 'none' : '';
		var count = line.children.length;
		Array.prototype.forEach.call(line.children, function (li, i) {
			li.dataset.step = target.ordered ? (count - i) + '차' : target.tag;
		});
		refreshOrg();
	}

	// 결재선은 나중에 넣은 사람이 다음 결재자이므로 끌어다 놓아도 항상 맨 위로 들어간다. 참조자는 맨 끝
	function add() {
		if (!orgSelected) { alert('왼쪽 조직도에서 사람을 선택하세요.'); return; }
		if (line.querySelector('[data-id="' + orgSelected.dataset.id + '"]')) { alert('이미 목록에 있습니다.'); return; }
		if (line.children.length >= target.max) { alert(target.max + '명까지 지정할 수 있습니다.'); return; }
		line.insertBefore(lineItem(person(orgSelected)), newPersonBefore());
		refreshLine();
	}

	function newPersonBefore() {
		return target.ordered ? line.firstElementChild : null;
	}

	function selectLine(li) {
		if (lineSelected) lineSelected.classList.remove('selected');
		lineSelected = li;
		if (li) li.classList.add('selected');
	}

	line.addEventListener('click', function (e) {
		var li = e.target.closest('li');
		if (!li) return;
		if (e.target.closest('button')) {
			if (lineSelected === li) lineSelected = null;
			li.remove();
			refreshLine();
			return;
		}
		selectLine(li);
	});
	line.addEventListener('dblclick', function (e) {
		var li = e.target.closest('li');
		if (li) { if (lineSelected === li) lineSelected = null; li.remove(); refreshLine(); }
	});

	document.getElementById('pickAdd').addEventListener('click', function () { add(); });
	document.getElementById('pickRemove').addEventListener('click', function () {
		if (!lineSelected) { alert('오른쪽 목록에서 뺄 사람을 선택하세요.'); return; }
		lineSelected.remove();
		lineSelected = null;
		refreshLine();
	});
	document.getElementById('pickUp').addEventListener('click', function () {
		if (lineSelected && lineSelected.previousElementSibling) {
			line.insertBefore(lineSelected, lineSelected.previousElementSibling);
			refreshLine();
		}
	});
	document.getElementById('pickDown').addEventListener('click', function () {
		if (lineSelected && lineSelected.nextElementSibling) {
			line.insertBefore(lineSelected.nextElementSibling, lineSelected);
			refreshLine();
		}
	});

	// ----- 끌어다 놓기 -----

	var linePane = line.closest('.picker-pane');
	var orgPane = org.closest('.picker-pane');

	function clearDropMarks() {
		Array.prototype.forEach.call(line.children, function (li) { li.classList.remove('drop-before'); });
		line.classList.remove('drop-end');
		orgPane.classList.remove('drop-out');
	}

	// 놓을 자리: 이 li 앞 (null 이면 맨 끝). 조직도에서 온 사람은 위치와 상관없고, 목록 안에서는 마우스 위치를 따른다
	function dropBefore(y) {
		if (dragged.parentNode !== line) return newPersonBefore();
		for (var i = 0; i < line.children.length; i++) {
			var li = line.children[i];
			if (li === dragged) continue;
			var box = li.getBoundingClientRect();
			if (y < box.top + box.height / 2) return li;
		}
		return null;
	}

	function startDrag(e, li) {
		dragged = li;
		e.dataTransfer.effectAllowed = 'move';
		e.dataTransfer.setData('text/plain', li.dataset.label);
		li.classList.add('dragging');
	}

	org.addEventListener('dragstart', function (e) {
		var member = e.target.closest('.pick-member');
		if (!member) return;
		selectOrg(member);
		startDrag(e, member);
	});
	line.addEventListener('dragstart', function (e) {
		var li = e.target.closest('li');
		if (!li) return;
		selectLine(li);
		startDrag(e, li);
	});
	dialog.addEventListener('dragend', function () {
		if (dragged) dragged.classList.remove('dragging');
		dragged = null;
		clearDropMarks();
	});

	linePane.addEventListener('dragover', function (e) {
		if (!dragged) return;
		e.preventDefault();
		clearDropMarks();
		var before = dropBefore(e.clientY);
		if (before) before.classList.add('drop-before');
		else line.classList.add('drop-end');
	});
	linePane.addEventListener('dragleave', function (e) {
		if (!linePane.contains(e.relatedTarget)) clearDropMarks();
	});
	linePane.addEventListener('drop', function (e) {
		if (!dragged) return;
		e.preventDefault();
		var before = dropBefore(e.clientY);
		clearDropMarks();
		if (dragged.parentNode === line) {
			line.insertBefore(dragged, before);
			refreshLine();
		} else {
			add();
		}
	});

	// 오른쪽 목록의 사람을 조직도 쪽에 놓으면 뺀다
	orgPane.addEventListener('dragover', function (e) {
		if (!dragged || dragged.parentNode !== line) return;
		e.preventDefault();
		orgPane.classList.add('drop-out');
	});
	orgPane.addEventListener('dragleave', function (e) {
		if (!orgPane.contains(e.relatedTarget)) orgPane.classList.remove('drop-out');
	});
	orgPane.addEventListener('drop', function (e) {
		if (!dragged || dragged.parentNode !== line) return;
		e.preventDefault();
		if (lineSelected === dragged) lineSelected = null;
		dragged.remove();
		clearDropMarks();
		refreshLine();
	});

	// ----- 열기 / 확인 / 닫기 -----

	document.querySelectorAll('[data-picker]').forEach(function (button) {
		var list = document.getElementById(button.dataset.list);
		var empty = document.getElementById(button.dataset.empty);
		var stamp = button.dataset.stamp ? document.getElementById(button.dataset.stamp) : null;
		refreshEmpty(list, empty);
		if (stamp) renderLineStamp(stamp, list);
		button.addEventListener('click', function () {
			target = {
				list: list, empty: empty, input: button.dataset.input, max: parseInt(button.dataset.max, 10),
				ordered: button.dataset.ordered === 'true', stamp: stamp, tag: button.dataset.tag || '참조'
			};
			title.textContent = button.dataset.title;
			paneTitle.textContent = target.ordered ? '결재선' : button.dataset.pane || '참조자';
			order.style.display = target.ordered ? '' : 'none';
			document.getElementById('pickOrderHint').style.display = target.ordered ? '' : 'none';
			line.innerHTML = '';
			Array.prototype.forEach.call(list.children, function (li) {
				line.insertBefore(lineItem(person(li)), target.ordered ? line.firstElementChild : null);
			});
			selectLine(null);
			if (orgSelected) { orgSelected.classList.remove('selected'); orgSelected = null; }
			search.value = '';
			refreshLine();
			dialog.showModal();
			search.focus();
		});
	});

	document.getElementById('pickConfirm').addEventListener('click', function () {
		target.list.innerHTML = '';
		var picked = Array.prototype.slice.call(line.children);
		if (target.ordered) picked.reverse();
		picked.forEach(function (src) {
			var p = person(src);
			var li = document.createElement('li');
			li.dataset.id = p.id;
			li.dataset.name = p.name;
			li.dataset.grade = p.grade;
			li.dataset.label = p.label;
			var input = document.createElement('input');
			input.type = 'hidden';
			input.name = target.input;
			input.value = p.id;
			var name = document.createElement('span');
			name.className = 'line-name';
			name.textContent = p.label;
			li.appendChild(input);
			li.appendChild(name);
			target.list.appendChild(li);
		});
		refreshEmpty(target.list, target.empty);
		if (target.stamp) renderLineStamp(target.stamp, target.list);
		dialog.close();
	});

	dialog.querySelectorAll('[data-close]').forEach(function (b) {
		b.addEventListener('click', function () { dialog.close(); });
	});
})();
