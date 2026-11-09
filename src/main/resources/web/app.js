'use strict';
let state = null;
let activeTab = 'overview';
let busy = false;
const $ = (selector) => document.querySelector(selector);
const $$ = (selector) => [...document.querySelectorAll(selector)];
const node = (tag, text, className) => { const item = document.createElement(tag); if (text !== undefined) item.textContent = text; if (className) item.className = className; return item; };
const titles = {overview: ['Directory overview', 'A small directory. Real administration skills.'], users: ['Directory users', 'Create identities, reset passwords, and unlock accounts.'], groups: ['Groups & permissions', 'Give people the access they need through security groups.'], ous: ['Organizational units', 'Build a clear structure for your lab directory.'], activity: ['Session activity', 'A clear record of your successful directory changes.']};
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
    const status = node('td'); status.append(node('span', u.locked ? 'Locked' : u.enabled ? 'Active' : 'Disabled', `status ${u.locked ? 'locked' : u.enabled ? 'active-status' : ''}`)); row.append(status);
    const actions = node('td'); const button = node('button', 'Manage →', 'text-button'); button.setAttribute('aria-label', `Manage ${u.username}`); button.addEventListener('click', () => openAccount(u.username)); actions.append(button); row.append(actions); body.append(row);
  }
  if (!users.length) { const row = node('tr'), cell = node('td', 'No users match your search.', 'empty'); cell.colSpan = 5; row.append(cell); body.append(row); }
  $('#user-summary').textContent = `${users.length} of ${state.users.length} users`;
}
function activity(target, events) {
  target.replaceChildren();
  if (!events.length) { target.append(node('p', 'Your changes will appear here. Start by unlocking a demo account or creating a user.', 'empty')); return; }
  for (const event of events) { const item = node('div', undefined, 'activity-item'); const details = node('div'); details.append(node('strong', event.action.replaceAll('-', ' ')), node('span', event.target)); item.append(node('span', '✓', 'activity-check'), details, node('time', new Date(event.time).toLocaleTimeString([], {hour:'2-digit',minute:'2-digit'}))); target.append(item); }
}
function render() {
  $('#mode').textContent = state.mode === 'demo' ? 'Demo environment' : 'Connected to Active Directory';
  $('#environment-title').textContent = state.mode === 'demo' ? 'Your practice environment is ready' : 'Connected to your Active Directory lab';
  $('#environment-description').textContent = state.mode === 'demo' ? 'Fictional users, in-memory data. Changes reset when you restart the Java app.' : 'Changes are written to the configured lab OU over LDAPS. Review each operation before submitting.';
  $('#user-count').textContent = state.users.length; $('#group-count').textContent = state.groups.length; $('#ou-count').textContent = state.ous.length;
  const locked = state.users.filter(u => u.locked).length; $('#locked-count').textContent = locked; $('#locked-caption').textContent = locked ? `${locked} ${locked === 1 ? 'account needs' : 'accounts need'} attention` : 'All accounts clear';
  options('.ou-select', state.ous); options('.user-select', state.users.map(u => u.username)); options('.group-select', state.groups.map(g => g.name));
  renderUsers();
  $('#group-cards').replaceChildren();
  state.groups.forEach(g => { const card = node('article', undefined, 'card group-card'); card.append(node('span', g.ou, 'eyebrow'), node('h2', g.name), node('p', `${g.members.length} direct ${g.members.length === 1 ? 'member' : 'members'}`)); const list = node('ul'); g.members.forEach(m => list.append(node('li', m))); if (!g.members.length) list.append(node('li', 'No members yet', 'empty')); card.append(list); $('#group-cards').append(card); });
  $('#ou-list').replaceChildren(); state.ous.forEach(ou => $('#ou-list').append(node('li', `▤  ${ou}`)));
  activity($('#recent-activity'), state.audit.slice(0, 3)); activity($('#all-activity'), state.audit);
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
