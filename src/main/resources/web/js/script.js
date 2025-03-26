window.onload = function () {
  loadJobs();
  loadSlots();
  loadApplications();
  loadInterviews();
};

let screeningSocket;
let interviewSocket;

function appendToConsole(msg) {
  const consoleBox = document.getElementById("debug-console");
  if (consoleBox) {
    const entry = document.createElement("div");
    entry.textContent = msg;
    consoleBox.appendChild(entry);
    consoleBox.scrollTop = consoleBox.scrollHeight;
  }
}

function startResumeStream() {
  if (screeningSocket && screeningSocket.readyState === WebSocket.OPEN) screeningSocket.close();

  screeningSocket = new WebSocket("ws://localhost:8080/ws/screening/submit");
  screeningSocket.onopen = () => {
    const resume = document.getElementById("resume").value;
    resume.split("\n").forEach(line => screeningSocket.send(line));
    screeningSocket.close();
    appendToConsole("[Resume Stream] Sent resume lines");
  };
  screeningSocket.onmessage = (msg) => {
    const resultBox = document.getElementById("apply-message");
    resultBox.textContent = msg.data;
    appendToConsole("[Resume Stream] " + msg.data);
  };
  screeningSocket.onerror = (err) => {
    document.getElementById("apply-message").textContent = "Error: " + err.message;
    appendToConsole("[Resume Stream Error] " + err.message);
  };
}

function startInterviewStream() {
  if (interviewSocket && interviewSocket.readyState === WebSocket.OPEN) interviewSocket.close();

  interviewSocket = new WebSocket("ws://localhost:8080/ws/interviews/schedule");
  interviewSocket.onopen = () => {
    const req = [
      document.getElementById("slot-name").value,
      document.getElementById("slot-email").value,
      "0",
      document.getElementById("slot-select").value,
    ];
    interviewSocket.send(req.join(","));
    appendToConsole("[Interview Stream] Sent scheduling request");
  };
  interviewSocket.onmessage = (msg) => {
    document.getElementById("schedule-message").textContent = msg.data;
    loadSlots();
    loadInterviews();
    appendToConsole("[Interview Stream] " + msg.data);
  };
  interviewSocket.onerror = (err) => {
    document.getElementById("schedule-message").textContent = "Error: " + err.message;
    appendToConsole("[Interview Stream Error] " + err.message);
  };
}

async function createJob() {
  const job = {
    title: document.getElementById("job-title").value,
    company: document.getElementById("job-company").value,
    description: document.getElementById("job-desc").value,
  };
  await fetch("/jobs", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(job),
  });
  document.getElementById("job-message").textContent = "Job created!";
  appendToConsole("[Job Created] " + job.title);
  loadJobs();
}

async function loadJobs() {
  const res = await fetch("/jobs");
  const jobs = await res.json();
  const table = document.getElementById("job-table");
  const select = document.getElementById("job-select");
  table.innerHTML = "<thead><tr><th>ID</th><th>Title</th><th>Company</th></tr></thead><tbody>";
  select.innerHTML = "";
  jobs.forEach((job) => {
    table.innerHTML += `<tr><td>${job.id}</td><td>${job.title}</td><td>${job.company}</td></tr>`;
    select.innerHTML += `<option value="${job.id}">${job.title} @ ${job.company}</option>`;
  });
  table.innerHTML += "</tbody>";
}

async function apply() {
  const app = {
    jobId: parseInt(document.getElementById("job-select").value),
    candidateName: document.getElementById("cand-name").value,
    candidateEmail: document.getElementById("cand-email").value,
    resumeText: document.getElementById("resume").value,
  };
  const res = await fetch("/apply", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(app),
  });
  const result = await res.json();
  document.getElementById("apply-message").textContent = result.message;
  appendToConsole("[Apply Submitted] " + app.candidateName);
  loadApplications();
  startResumeStream();
}

async function loadSlots() {
  const res = await fetch("/slots");
  const slots = await res.json();
  const select = document.getElementById("slot-select");
  select.innerHTML = "";
  slots.forEach((s) => {
    select.innerHTML += `<option value="${s.slotId}">${s.time}</option>`;
  });
}

async function schedule() {
  appendToConsole("[Schedule Clicked]");
  startInterviewStream();
}

async function loadApplications() {
  const res = await fetch("/applications");
  const applications = await res.json();
  const table = document.getElementById("applications-table").getElementsByTagName("tbody")[0];
  table.innerHTML = "";
  applications.forEach((app) => {
    table.innerHTML += `<tr><td>${app.candidateName}</td><td>${app.candidateEmail}</td><td>${app.jobId}</td><td>${app.screeningScore}</td><td>${app.screeningFeedback}</td></tr>`;
  });
}

async function loadInterviews() {
  const res = await fetch("/interviews");
  const interviews = await res.json();
  const table = document.getElementById("interview-table").getElementsByTagName("tbody")[0];
  table.innerHTML = "";
  interviews.forEach((i) => {
    table.innerHTML += `<tr><td>${i.candidateName}</td><td>${i.candidateEmail}</td><td>${i.slotId}</td><td>${i.time}</td></tr>`;
  });
}
