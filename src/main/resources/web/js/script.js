window.onload = function () {
  loadJobs();
  loadSlots();
  loadApplications();
  loadInterviews();
};

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
  loadApplications();
}

async function checkScreening() {
  const email = document.getElementById("screen-email").value;
  const res = await fetch(`/screening?email=${encodeURIComponent(email)}`);
  const result = await res.json();
  document.getElementById("screen-result").textContent = `Score: ${result.score}, Feedback: ${result.feedback}`;
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
  const req = {
    slotId: document.getElementById("slot-select").value,
    candidateName: document.getElementById("slot-name").value,
    candidateEmail: document.getElementById("slot-email").value,
    jobId: 0,
  };
  const res = await fetch("/schedule", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(req),
  });
  const result = await res.json();
  document.getElementById("schedule-message").textContent = result.message;
  loadSlots();
  loadInterviews();
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
