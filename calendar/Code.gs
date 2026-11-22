// Glass Home calendar bridge: lets the TV read your upcoming appointments and add new ones.
// 1. Go to script.google.com, create a new project and paste this file in.
// 2. Replace PUT-YOUR-OWN-KEY-HERE with a long secret of your own.
// 3. Deploy > New deployment > Web app. Execute as: Me. Who has access: Anyone.
// 4. Give the TV the web app link with ?key=YOUR-KEY added to the end.
const KEY = 'PUT-YOUR-OWN-KEY-HERE';

function doGet(e) {
  const p = (e && e.parameter) || {};
  if (KEY === 'PUT-YOUR-OWN-KEY-HERE' || p.key !== KEY) return out({error: 'wrong key'});
  const cal = CalendarApp.getDefaultCalendar();
  if (p.add) {
    // Quick Add understands plain sentences such as "Dentist on Friday at 3 pm".
    const ev = cal.createEventFromDescription(p.add);
    return out({added: ev.getTitle(), start: ev.getStartTime().getTime(), allDay: ev.isAllDayEvent()});
  }
  const now = new Date();
  const until = new Date(now.getTime() + 30 * 24 * 3600 * 1000);
  const events = cal.getEvents(now, until).slice(0, 20).map(function (ev) {
    return {title: ev.getTitle(), start: ev.getStartTime().getTime(), allDay: ev.isAllDayEvent()};
  });
  return out({events: events});
}

function out(o) {
  return ContentService.createTextOutput(JSON.stringify(o)).setMimeType(ContentService.MimeType.JSON);
}
