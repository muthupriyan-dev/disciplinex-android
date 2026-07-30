// ==========================================================================
// உறவுசுவடி — app.js
// Family-shared data via Firestore: families/{familyId}/people|events|entries
// ==========================================================================
import {
  db, doc, getDoc, addDoc, updateDoc, deleteDoc,
  collection, query, where, orderBy, onSnapshot, serverTimestamp
} from "./firebase-config.js";

function $(id) { return document.getElementById(id); }
function show(el) { el && el.classList.remove("hidden"); }
function hide(el) { el && el.classList.add("hidden"); }
function fmt(n) { return "₹" + Number(n || 0).toLocaleString("en-IN"); }

let PEOPLE = [];
let EVENTS = [];
let ENTRIES = [];
let unsubs = [];

window.bootApp = function bootApp() {
  unsubs.forEach(u => u());
  unsubs = [];

  $("familyNameLabel").textContent = window.CURRENT_FAMILY?.name || "குடும்ப மொய் பதிவேடு";
  $("settingsUserName").textContent = window.CURRENT_USER?.name || "—";
  $("settingsUserEmail").textContent = window.CURRENT_USER?.email || "—";
  $("settingsFamilyName").textContent = window.CURRENT_FAMILY?.name || "—";
  $("settingsJoinCode").textContent = window.CURRENT_FAMILY?.joinCode || "—";

  const famId = window.CURRENT_FAMILY_ID;

  const peopleQ = query(collection(db, "families", famId, "people"), orderBy("name"));
  unsubs.push(onSnapshot(peopleQ, (snap) => {
    PEOPLE = snap.docs.map(d => ({ id: d.id, ...d.data() }));
    renderPeople();
    renderDashboard();
  }));

  const eventsQ = query(collection(db, "families", famId, "events"), orderBy("date", "desc"));
  unsubs.push(onSnapshot(eventsQ, (snap) => {
    EVENTS = snap.docs.map(d => ({ id: d.id, ...d.data() }));
    renderEvents();
    fillEventSelect();
    renderDashboard();
  }));

  const entriesQ = query(collection(db, "families", famId, "entries"), orderBy("date", "desc"));
  unsubs.push(onSnapshot(entriesQ, (snap) => {
    ENTRIES = snap.docs.map(d => ({ id: d.id, ...d.data() }));
    renderEntries();
    renderDashboard();
  }));
};

// ==========================================================================
// NAVIGATION
// ==========================================================================
document.addEventListener("click", (e) => {
  const goBtn = e.target.closest("[data-go]");
  if (goBtn) {
    const target = goBtn.getAttribute("data-go");
    document.querySelectorAll(".screen").forEach(s => hide(s));
    show($(target));
    document.querySelectorAll(".nav-btn").forEach(n => n.classList.toggle("active", n.getAttribute("data-go") === target));
  }
});

$("copyJoinCodeBtn").addEventListener("click", () => {
  const code = window.CURRENT_FAMILY?.joinCode || "";
  navigator.clipboard?.writeText(code);
  toast("Code Copy ஆனது — share பண்ணுங்க!", "success");
});

// ==========================================================================
// DASHBOARD
// ==========================================================================
function renderDashboard() {
  const given = ENTRIES.filter(e => e.type === "given").reduce((s, e) => s + Number(e.amountNew || 0), 0);
  const received = ENTRIES.filter(e => e.type === "received").reduce((s, e) => s + Number(e.amountNew || 0), 0);
  $("statReceived").textContent = fmt(received);
  $("statGiven").textContent = fmt(given);
  $("statBalance").textContent = fmt(received - given);
  $("statEvents").textContent = EVENTS.length;
  $("statPeople").textContent = PEOPLE.length;
  $("statRecords").textContent = ENTRIES.length;

  const recent = ENTRIES.slice(0, 5);
  $("recentEntries").innerHTML = recent.length ? recent.map(entryCardHtml).join("") :
    `<div class="empty-state"><div class="es-icon">📋</div><p>இன்னும் பதிவுகள் இல்லை</p></div>`;
  bindEntryCardClicks($("recentEntries"));
}

// ==========================================================================
// PEOPLE
// ==========================================================================
function renderPeople() {
  const term = ($("peopleSearch").value || "").trim().toLowerCase();
  const filtered = PEOPLE.filter(p => !term || p.name.toLowerCase().includes(term));
  $("peopleList").innerHTML = filtered.length ? filtered.map(personCardHtml).join("") :
    `<div class="empty-state"><div class="es-icon">👥</div><p>நபர்கள் இல்லை</p></div>`;
  bindPersonCardClicks();
}
$("peopleSearch").addEventListener("input", renderPeople);

function personCardHtml(p) {
  const initial = (p.name || "?").trim()[0]?.toUpperCase() || "?";
  const count = ENTRIES.filter(e => e.personId === p.id).length;
  return `
  <div class="person-card" data-id="${p.id}">
    <div class="avatar">${initial}</div>
    <div class="person-main">
      <div class="p-name">${escapeHtml(p.name)}</div>
      <div class="p-sub">${escapeHtml(p.relation || "")}${p.relation ? " · " : ""}${count} பதிவுகள்</div>
    </div>
    <div class="row-actions">
      <button class="icon-mini edit-person" data-id="${p.id}">✎</button>
      <button class="icon-mini danger del-person" data-id="${p.id}">🗑</button>
    </div>
  </div>`;
}

function bindPersonCardClicks() {
  document.querySelectorAll(".edit-person").forEach(b => b.onclick = (e) => {
    e.stopPropagation();
    openPersonModal(PEOPLE.find(p => p.id === b.dataset.id));
  });
  document.querySelectorAll(".del-person").forEach(b => b.onclick = async (e) => {
    e.stopPropagation();
    if (!confirm("இந்த நபரை நீக்கவா?")) return;
    await deleteDoc(doc(db, "families", window.CURRENT_FAMILY_ID, "people", b.dataset.id));
    toast("நீக்கப்பட்டது", "success");
  });
}

$("addPersonFab").addEventListener("click", () => openPersonModal(null));

function openPersonModal(person) {
  $("personModalTitle").textContent = person ? "நபரை திருத்து" : "நபரைச் சேர்";
  $("personId").value = person?.id || "";
  $("personName").value = person?.name || "";
  $("personRelation").value = person?.relation || "";
  $("personPhone").value = person?.phone || "";
  show($("personModal"));
}

$("personForm").addEventListener("submit", async (e) => {
  e.preventDefault();
  const id = $("personId").value;
  const data = {
    name: $("personName").value.trim(),
    relation: $("personRelation").value.trim(),
    phone: $("personPhone").value.trim(),
  };
  const famId = window.CURRENT_FAMILY_ID;
  if (id) {
    await updateDoc(doc(db, "families", famId, "people", id), data);
  } else {
    await addDoc(collection(db, "families", famId, "people"), { ...data, createdAt: serverTimestamp() });
  }
  hide($("personModal"));
  toast("சேமிக்கப்பட்டது", "success");
});

// ==========================================================================
// EVENTS
// ==========================================================================
const eventTypeLabel = { marriage: "திருமணம்", puberty: "பூப்புனிதம்", housewarming: "வீடு புகுவிழா", birthday: "பிறந்தநாள்", other: "மற்றவை" };

function renderEvents() {
  const term = ($("eventsSearch").value || "").trim().toLowerCase();
  const filtered = EVENTS.filter(ev => !term || ev.title.toLowerCase().includes(term));
  $("eventsList").innerHTML = filtered.length ? filtered.map(eventCardHtml).join("") :
    `<div class="empty-state"><div class="es-icon">🎉</div><p>நிகழ்வுகள் இல்லை</p></div>`;
  bindEventCardClicks();
}
$("eventsSearch").addEventListener("input", renderEvents);

function eventCardHtml(ev) {
  return `
  <div class="event-card" data-id="${ev.id}">
    <div class="avatar">🎉</div>
    <div class="event-main">
      <div class="e-title">${escapeHtml(ev.title)}</div>
      <div class="e-sub">${eventTypeLabel[ev.type] || ev.type} · ${ev.date || ""}</div>
    </div>
    <div class="row-actions">
      <button class="icon-mini edit-event" data-id="${ev.id}">✎</button>
      <button class="icon-mini danger del-event" data-id="${ev.id}">🗑</button>
    </div>
  </div>`;
}

function bindEventCardClicks() {
  document.querySelectorAll(".edit-event").forEach(b => b.onclick = (e) => {
    e.stopPropagation();
    openEventModal(EVENTS.find(ev => ev.id === b.dataset.id));
  });
  document.querySelectorAll(".del-event").forEach(b => b.onclick = async (e) => {
    e.stopPropagation();
    if (!confirm("இந்த நிகழ்வை நீக்கவா?")) return;
    await deleteDoc(doc(db, "families", window.CURRENT_FAMILY_ID, "events", b.dataset.id));
    toast("நீக்கப்பட்டது", "success");
  });
}

$("addEventFab").addEventListener("click", () => openEventModal(null));

function openEventModal(ev) {
  $("eventModalTitle").textContent = ev ? "நிகழ்வை திருத்து" : "நிகழ்வைச் சேர்";
  $("eventId").value = ev?.id || "";
  $("eventTitle").value = ev?.title || "";
  $("eventType").value = ev?.type || "marriage";
  $("eventDate").value = ev?.date || new Date().toISOString().slice(0, 10);
  show($("eventModal"));
}

$("eventForm").addEventListener("submit", async (e) => {
  e.preventDefault();
  const id = $("eventId").value;
  const data = {
    title: $("eventTitle").value.trim(),
    type: $("eventType").value,
    date: $("eventDate").value,
  };
  const famId = window.CURRENT_FAMILY_ID;
  if (id) {
    await updateDoc(doc(db, "families", famId, "events", id), data);
  } else {
    await addDoc(collection(db, "families", famId, "events"), { ...data, createdAt: serverTimestamp() });
  }
  hide($("eventModal"));
  toast("சேமிக்கப்பட்டது", "success");
});

function fillEventSelect() {
  $("entryEventSelect").innerHTML = EVENTS.map(ev => `<option value="${ev.id}">${escapeHtml(ev.title)} (${ev.date || ""})</option>`).join("")
    || `<option value="">— நிகழ்வுகள் இல்லை, முதலில் ஒன்று சேருங்க —</option>`;
}

// ==========================================================================
// RECORDS / ENTRIES  (priority: RECEIVED first, GIVEN second)
// ==========================================================================
let recordsFilterType = "all";

function renderEntries() {
  const term = ($("recordsSearch").value || "").trim().toLowerCase();
  let filtered = ENTRIES.filter(e => recordsFilterType === "all" || e.type === recordsFilterType);
  if (term) {
    filtered = filtered.filter(e =>
      (e.personName || "").toLowerCase().includes(term) ||
      (e.eventTitle || "").toLowerCase().includes(term)
    );
  }
  $("recordsListEl").innerHTML = filtered.length ? filtered.map(entryCardHtml).join("") :
    `<div class="empty-state"><div class="es-icon">📋</div><p>பதிவுகள் இல்லை</p></div>`;
  bindEntryCardClicks($("recordsListEl"));
}
$("recordsSearch").addEventListener("input", renderEntries);
document.querySelectorAll(".tab-btn[data-rtype]").forEach(btn => {
  btn.addEventListener("click", () => {
    recordsFilterType = btn.dataset.rtype;
    document.querySelectorAll(".tab-btn[data-rtype]").forEach(b => b.classList.toggle("active", b === btn));
    renderEntries();
  });
});

function entryCardHtml(en) {
  const isReceived = en.type === "received";
  return `
  <div class="entry-card ${isReceived ? "type-received" : "type-given"}" data-id="${en.id}">
    <div class="entry-icon">${isReceived ? "📥" : "📤"}</div>
    <div class="entry-main">
      <div class="en-title">${escapeHtml(en.personName || "")}</div>
      <div class="en-sub">${escapeHtml(en.eventTitle || "")} · ${en.date || ""}</div>
    </div>
    <div class="entry-amount ${isReceived ? "received" : "given"}">${fmt(en.amountNew)}</div>
  </div>`;
}

function bindEntryCardClicks(root) {
  root.querySelectorAll(".entry-card").forEach(card => {
    card.onclick = () => openEntryModal(ENTRIES.find(e => e.id === card.dataset.id));
  });
}

$("addRecordFab").addEventListener("click", () => openEntryModal(null));

let selectedEntryType = "received";
document.querySelectorAll(".radio-pill[data-entrytype]").forEach(pill => {
  pill.addEventListener("click", () => {
    selectedEntryType = pill.dataset.entrytype;
    $("entryType").value = selectedEntryType;
    document.querySelectorAll(".radio-pill[data-entrytype]").forEach(p => p.classList.toggle("selected", p === pill));
  });
});

function openEntryModal(en) {
  $("entryModalTitle").textContent = en ? "பதிவை திருத்து" : "பதிவு சேர் (Add Record)";
  $("entryId").value = en?.id || "";
  selectedEntryType = en?.type || "received";
  $("entryType").value = selectedEntryType;
  document.querySelectorAll(".radio-pill[data-entrytype]").forEach(p =>
    p.classList.toggle("selected", p.dataset.entrytype === selectedEntryType));
  $("entryPersonInput").value = en?.personName || "";
  $("entryPersonId").value = en?.personId || "";
  fillEventSelect();
  if (en?.eventId) $("entryEventSelect").value = en.eventId;
  $("entryAmountOld").value = en?.amountOld || "";
  $("entryAmountNew").value = en?.amountNew || "";
  $("entryDate").value = en?.date || new Date().toISOString().slice(0, 10);
  $("entryNotes").value = en?.notes || "";
  show($("entryModal"));
}

$("entryForm").addEventListener("submit", async (e) => {
  e.preventDefault();
  const id = $("entryId").value;
  const famId = window.CURRENT_FAMILY_ID;
  const evSel = $("entryEventSelect");
  const data = {
    type: $("entryType").value,
    personId: $("entryPersonId").value || null,
    personName: $("entryPersonInput").value.trim(),
    eventId: evSel.value || null,
    eventTitle: evSel.options[evSel.selectedIndex]?.text || "",
    amountOld: Number($("entryAmountOld").value || 0),
    amountNew: Number($("entryAmountNew").value || 0),
    date: $("entryDate").value,
    notes: $("entryNotes").value.trim(),
  };

  // auto-create person if typed a brand-new name
  if (!data.personId && data.personName) {
    const existing = PEOPLE.find(p => p.name.toLowerCase() === data.personName.toLowerCase());
    if (existing) {
      data.personId = existing.id;
    } else {
      const newPersonRef = await addDoc(collection(db, "families", famId, "people"), {
        name: data.personName, relation: "", phone: "", createdAt: serverTimestamp()
      });
      data.personId = newPersonRef.id;
    }
  }

  if (id) {
    await updateDoc(doc(db, "families", famId, "entries", id), data);
  } else {
    await addDoc(collection(db, "families", famId, "entries"), { ...data, createdAt: serverTimestamp() });
  }
  hide($("entryModal"));
  toast("பதிவு சேமிக்கப்பட்டது", "success");
});

// ---- person name autocomplete (type first letter -> suggestions, tap to fill) ----
const acInput = $("entryPersonInput");
const acList = $("personAutocompleteList");

acInput.addEventListener("input", () => {
  const term = acInput.value.trim().toLowerCase();
  $("entryPersonId").value = ""; // typing manually clears the linked id until they pick/confirm
  if (!term) { hide(acList); return; }
  const matches = PEOPLE.filter(p => p.name.toLowerCase().startsWith(term)).slice(0, 8);
  if (!matches.length) {
    acList.innerHTML = `<div class="autocomplete-empty">பொருந்தும் நபர் இல்லை — "${escapeHtml(acInput.value)}" புதிய நபராக சேர்க்கப்படும்</div>`;
  } else {
    acList.innerHTML = matches.map(p => `<div class="autocomplete-item" data-id="${p.id}" data-name="${escapeHtml(p.name)}">${escapeHtml(p.name)}${p.relation ? ` <span class="muted">(${escapeHtml(p.relation)})</span>` : ""}</div>`).join("");
  }
  show(acList);
});

acList.addEventListener("click", (e) => {
  const item = e.target.closest(".autocomplete-item");
  if (!item || !item.dataset.id) return;
  acInput.value = item.dataset.name;
  $("entryPersonId").value = item.dataset.id;
  hide(acList);
});

document.addEventListener("click", (e) => {
  if (!e.target.closest(".autocomplete-wrap")) hide(acList);
});

// ==========================================================================
// GLOBAL SEARCH
// ==========================================================================
$("globalSearchBtn").addEventListener("click", () => { show($("searchOverlay")); $("globalSearchInput").focus(); });
$("closeSearchBtn").addEventListener("click", () => hide($("searchOverlay")));
$("globalSearchInput").addEventListener("input", () => {
  const term = $("globalSearchInput").value.trim().toLowerCase();
  const box = $("globalSearchResults");
  if (!term) { box.innerHTML = `<div class="search-empty">தேட ஆரம்பிக்கவும்...</div>`; return; }
  const peopleHits = PEOPLE.filter(p => p.name.toLowerCase().includes(term))
    .map(p => `<div class="search-result-item"><div class="sr-title">${escapeHtml(p.name)}</div><div class="sr-sub">நபர் · ${escapeHtml(p.relation || "")}</div></div>`);
  const entryHits = ENTRIES.filter(e => (e.personName || "").toLowerCase().includes(term) || (e.eventTitle || "").toLowerCase().includes(term))
    .map(e => `<div class="search-result-item"><div class="sr-title">${escapeHtml(e.personName)} — ${fmt(e.amountNew)}</div><div class="sr-sub">${escapeHtml(e.eventTitle || "")} · ${e.date || ""}</div></div>`);
  const all = [...peopleHits, ...entryHits];
  box.innerHTML = all.length ? all.join("") : `<div class="search-empty">பொருத்தம் இல்லை</div>`;
});

// ==========================================================================
// MODAL CLOSE buttons
// ==========================================================================
document.querySelectorAll("[data-close]").forEach(btn => {
  btn.addEventListener("click", () => hide($(btn.dataset.close)));
});

function escapeHtml(str) {
  return String(str || "").replace(/[&<>"']/g, c => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" }[c]));
}
