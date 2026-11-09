'use strict';
let state = null;
let activeTab = 'overview';
let busy = false;
const $ = (selector) => document.querySelector(selector);
const $$ = (selector) => [...document.querySelectorAll(selector)];
const node = (tag, text, className) => { const item = document.createElement(tag); if (text !== undefined) item.textContent = text; if (className) item.className = className; return item; };
const titles = {overview: ['Directory overview', 'A small directory. Real administration skills.'], users: ['Directory users', 'Create identities, reset passwords, and unlock accounts.'], groups: ['Groups & permissions', 'Give people the access they need through security groups.'], ous: ['Organizational units', 'Build a clear structure for your lab directory.'], tickets: ['Help desk tickets', 'Practice realistic requests. Verify each outcome.'], activity: ['Saved activity', 'Attempts, outcomes, and a history you can export.'], results: ['Results & evidence', 'Document what you completed and what still needs verification.']};
function notify(message, error = false) { const el = $('#notice'); el.textContent = message; el.className = `notice ${error ? 'error' : 'success'}`; el.hidden = false; }
function showTab(tab) {
  activeTab = tab;
  $$('.nav-item').forEach(b => { b.classList.toggle('active', b.dataset.tab === tab); b.setAttribute('aria-current', b.dataset.tab === tab ? 'page' : 'false'); });
  $$('.panel').forEach(p => { p.hidden = p.id !== `${tab === 'users' ? 'overview' : tab}-panel`; });
  const [title, description] = titles[tab]; $('#page-title').textContent = title; $('#page-description').textContent = description; $('#breadcrumb').textContent = tab === 'overview' ? 'Overview' : title;
}
function options(selector, values) {
  $$(selector).forEach(select => { const previous = select.value; select.replaceChildren(); if (!values.length) { const empty = node('option', 'Create one first'); empty.value = ''; select.append(empty); } values.forEach(value => { const item = node('option', value); item.value = value; select.append(item); }); if (values.includes(previous)) select.value = previous; });
}
function openAccount(username) {
  $('#account-title').textContent = username;
  $$('#account-dialog input[name="username"]').forEach(i => { i.value = username; });
  $('#account-dialog input[type="password"]').value = '';
  $('#lock-form').hidden = state.mode !== 'demo';
  const user = state.users.find(u => u.username === username);
  $('#account-dialog select[name="enabled"]').value = String(user.enabled);
  if (state.ous.includes(user.ou)) $('#account-dialog select[name="ou"]').value = user.ou;
  $('#account-dialog').showModal();
}
function renderUsers() {
  const query = $('#search').value.trim().toLowerCase();
  const users = state.users.filter(u => `${u.displayName} ${u.username} ${u.ou}`.toLowerCase().includes(query));
  const body = $('#user-rows'); body.replaceChildren();
  for (const u of users) {
    const row = node('tr'); const name = node('td'); const person = node('div', undefined, 'person');
    const initials = u.displayName.split(/\s+/).map(p => p[0]).slice(0, 2).join('');
    person.append(node('span', initials, `person-avatar color-${u.ou.length % 4}`), node('strong', u.displayName)); name.append(person); row.append(name);
    row.append(node('td', u.username, 'mono'), node('td', u.ou));
    const status = node('td'); status.append(node('span', !u.enabled ? 'Disabled' : u.locked ? 'Locked' : 'Active', `status ${!u.enabled ? '' : u.locked ? 'locked' : 'active-status'}`)); row.append(status);
    const actions = node('td'); const button = node('button', 'Manage →', 'text-button'); button.setAttribute('aria-label', `Manage ${u.username}`); button.addEventListener('click', () => openAccount(u.username)); actions.append(button); row.append(actions); body.append(row);
  }
  if (!users.length) { const row = node('tr'), cell = node('td', 'No users match your search.', 'empty'); cell.colSpan = 5; row.append(cell); body.append(row); }
  $('#user-summary').textContent = `${users.length} of ${state.users.length} users`;
}
function activity(target, events) {
  target.replaceChildren();
  if (!events.length) { target.append(node('p', 'Your changes will appear here. Start by unlocking a demo account or creating a user.', 'empty')); return; }
  for (const event of events) { const item = node('div', undefined, 'activity-item'); const details = node('div'); details.append(node('strong', event.action.replaceAll('-', ' ')), node('span', `${event.target} · ${event.detail}`)); const mark = event.outcome === 'SUCCEEDED' ? '✓' : event.outcome === 'STARTED' ? '…' : '!'; const outcome = node('span', event.outcome, `event-outcome ${event.outcome.toLowerCase()}`); item.append(node('span', mark, 'activity-check'), details, outcome, node('time', new Date(event.time).toLocaleString([], {month:'short',day:'numeric',hour:'2-digit',minute:'2-digit'}))); item.title = `Operation ${event.id}`; target.append(item); }
}
function renderActivity() {
  const filter = $('#audit-filter').value;
  activity($('#recent-activity'), state.audit.filter(e => e.outcome !== 'STARTED').slice(0, 3));
  activity($('#all-activity'), state.audit.filter(e => filter === 'all' || e.outcome === filter));
}
function renderTickets() {
  $('#ticket-cards').replaceChildren();
  const complete = state.tickets.filter(t => t.status === 'Checks satisfied').length;
  $('#ticket-summary').textContent = `${complete} of ${state.tickets.length} requests satisfy all checks`;
  for (const ticket of state.tickets) {
    const card = node('article', undefined, 'card ticket-card');
    const header = node('div', undefined, 'section-title'); header.append(node('span', ticket.id, 'eyebrow'), node('span', ticket.status, `status ${ticket.status === 'Open' ? 'locked' : 'active-status'}`));
    card.append(header, node('h2', ticket.title), node('p', ticket.request));
    const progress = node('progress'); progress.max = ticket.total; progress.value = ticket.completed; progress.setAttribute('aria-label', `${ticket.id}: ${ticket.completed} of ${ticket.total} checks`); card.append(progress);
    const list = node('ul', undefined, 'ticket-steps');
    for (const step of ticket.steps) { const item = node('li', undefined, step.complete ? 'complete' : ''); item.append(node('span', step.complete ? '✓' : '○'), node('span', step.instruction)); list.append(item); }
    card.append(list); const button = node('button', 'Open directory tools →', 'text-button'); button.addEventListener('click', () => showTab('users')); card.append(button); $('#ticket-cards').append(card);
  }
}
function render() {
  $('#mode').textContent = state.mode === 'demo' ? 'Demo environment' : 'Connected to Active Directory';
  $('#environment-title').textContent = state.mode === 'demo' ? 'Your practice environment is ready' : 'Connected to your Active Directory lab';
  $('#environment-description').textContent = state.mode === 'demo' ? 'Fictional users. Your directory changes and activity are saved locally across restarts.' : 'Changes are written to the configured lab OU over LDAPS. Review each operation before submitting.';
  $('#user-count').textContent = state.users.length; $('#group-count').textContent = state.groups.length; $('#ou-count').textContent = state.ous.length;
  const locked = state.users.filter(u => u.locked).length; $('#locked-count').textContent = locked; $('#locked-caption').textContent = locked ? `${locked} ${locked === 1 ? 'account needs' : 'accounts need'} attention` : 'All accounts clear';
  options('.ou-select', state.ous); options('.user-select', state.users.map(u => u.username)); options('.group-select', state.groups.map(g => g.name));
  renderUsers();
  $('#group-cards').replaceChildren();
  state.groups.forEach(g => { const card = node('article', undefined, 'card group-card'); card.append(node('span', g.ou, 'eyebrow'), node('h2', g.name), node('p', `${g.members.length} direct ${g.members.length === 1 ? 'member' : 'members'}`)); const list = node('ul'); g.members.forEach(m => list.append(node('li', m))); if (!g.members.length) list.append(node('li', 'No members yet', 'empty')); card.append(list); $('#group-cards').append(card); });
  $('#ou-list').replaceChildren(); state.ous.forEach(ou => $('#ou-list').append(node('li', `▤  ${ou}`)));
  renderActivity(); renderTickets();
  $('#results-mode').textContent = state.mode === 'demo' ? 'Current mode: DEMO. Exported results describe a simulated directory.' : 'Current mode: LDAP. Application observations still need independent Windows verification.';
}
async function refresh() {
  const response = await fetch('/api/state'); const data = await response.json();
  if (!response.ok) throw new Error(data.error || 'Could not load directory.');
  state = data; render();
}
$$('.nav-item').forEach(button => button.addEventListener('click', () => showTab(button.dataset.tab)));
$('#new-user').addEventListener('click', () => { if (state) $('#user-dialog').showModal(); });
$$('.close-dialog').forEach(button => button.addEventListener('click', () => button.closest('dialog').close()));
$('#search').addEventListener('input', () => { if (state) renderUsers(); });
$('#audit-filter').addEventListener('change', () => { if (state) renderActivity(); });
$('#view-activity').addEventListener('click', () => showTab('activity'));
$('#challenge').addEventListener('click', () => { if (!state) return; const user = state.users.find(u => u.locked) || state.users[0]; if (user) openAccount(user.username); else notify('Create a user first.'); });
$$('form[data-action]').forEach(form => form.addEventListener('submit', async event => {
  event.preventDefault(); if (busy || !state) return; busy = true;
  const button = form.querySelector('button[type="submit"],button:not([type])'); button.disabled = true;
  let updated = false;
  try {
    const values = new URLSearchParams(new FormData(form)); values.set('action', form.dataset.action);
    const response = await fetch('/api/action', {method:'POST', headers:{'X-Lab-Token':state.token}, body:values});
    const data = await response.json();
    if (!response.ok) throw new Error(data.error || 'Operation failed.');
    updated = true;
    form.querySelectorAll('input:not([type="hidden"])').forEach(input => { input.value = ''; });
    const dialog = form.closest('dialog'); if (dialog) dialog.close();
    try { await refresh(); notify(data.message); }
    catch { notify('The change succeeded, but the directory view could not refresh. Reload before making another change.', true); }
  } catch (error) {
    const dialog = form.closest('dialog'); if (dialog) dialog.close();
    form.querySelectorAll('input[type="password"]').forEach(input => { input.value = ''; });
    notify(error.message, true);
    if (!updated) { try { await refresh(); } catch { /* Keep the original error visible. */ } }
  } finally { busy = false; button.disabled = false; }
}));
refresh().catch(error => notify(error.message, true));
