(() => {
    "use strict";

    const form = document.querySelector("#advice-form");
    const messages = document.querySelector("#messages");
    const emptyState = document.querySelector("#empty-state");
    const formError = document.querySelector("#form-error");
    const submitButton = document.querySelector("#submit-button");
    const submitLabel = document.querySelector("#submit-label");
    const loadingLabel = document.querySelector("#loading-label");
    const storageKey = "smart-electricity-assistant-messages";
    const money = new Intl.NumberFormat("da-DK", { style: "currency", currency: "DKK" });
    const decimal = new Intl.NumberFormat("da-DK", { maximumFractionDigits: 2 });

    document.querySelector("#date").value = new Date().toISOString().slice(0, 10);

    const escapeHtml = (value) => String(value ?? "")
        .replaceAll("&", "&amp;").replaceAll("<", "&lt;")
        .replaceAll(">", "&gt;").replaceAll('"', "&quot;").replaceAll("'", "&#039;");

    const formatDateTime = (value) => {
        const date = new Date(value);
        return Number.isNaN(date.getTime()) ? "Unknown time" : new Intl.DateTimeFormat("en-DK", {
            dateStyle: "medium", timeStyle: "short"
        }).format(date);
    };

    const formatWindow = (window) => {
        if (!window || !window.priceWindow) return "No window available";
        return `${formatDateTime(window.priceWindow.startTime)} – ${formatDateTime(window.priceWindow.endTime)}`;
    };

    const saveMessages = (items) => sessionStorage.setItem(storageKey, JSON.stringify(items));
    const loadMessages = () => {
        try {
            const value = JSON.parse(sessionStorage.getItem(storageKey) || "[]");
            return Array.isArray(value) ? value : [];
        } catch {
            return [];
        }
    };

    const renderResult = (response) => {
        const explanation = response.explanation || {};
        const selected = response.selectedWindow;
        const cheapest = response.cheapestWindow;
        const isFallback = response.source === "CHEAPEST_FALLBACK";
        const selectedCost = selected && selected.estimatedCost != null ? money.format(selected.estimatedCost) : "Unavailable";
        const cheapestCost = cheapest && cheapest.estimatedCost != null ? money.format(cheapest.estimatedCost) : "Unavailable";
        const difference = Number(response.extraCostVsCheapestDkk || 0);
        const reasons = (explanation.reasons || []).map((reason) => `<li>${escapeHtml(reason)}</li>`).join("");
        const assumptions = (explanation.assumptions || []).map((assumption) => `<li>${escapeHtml(assumption)}</li>`).join("");
        return `<p><strong>${isFallback ? "Cheapest valid window used" : "Recommendation"}</strong></p>
            <div class="result-grid">
                <div class="result-card result-card-recommended"><span>Selected window</span><strong>${escapeHtml(formatWindow(selected))}</strong><span>Java-calculated spot-price estimate: ${escapeHtml(selectedCost)}</span></div>
                <div class="result-card"><span>Mathematically cheapest</span><strong>${escapeHtml(formatWindow(cheapest))}</strong><span>Java-calculated spot-price estimate: ${escapeHtml(cheapestCost)}</span></div>
                <div class="result-card"><span>Difference vs cheapest</span><strong>${escapeHtml(money.format(difference))}</strong><span>Based on estimated consumption</span></div>
                <div class="result-card"><span>Estimated appliance use</span><strong>${escapeHtml(decimal.format(explanation.estimatedKwh))} kWh</strong><span>Estimated, not measured</span></div>
            </div>
            <p>${escapeHtml(explanation.summary || "No explanation was provided.")}</p>
            ${reasons ? `<p><strong>Why this window?</strong></p><ul class="result-list">${reasons}</ul>` : ""}
            ${assumptions ? `<p><strong>Assumptions</strong></p><ul class="result-list">${assumptions}</ul>` : ""}
            <p class="result-note"><strong>Spot-price estimate:</strong> ${escapeHtml(response.priceNote || "This is an estimate based on retrieved spot prices, not a complete household electricity bill.")} Appliance consumption is estimated, not measured.</p>`;
    };

    const renderMessages = (items) => {
        emptyState.hidden = items.length > 0;
        messages.querySelectorAll(".message").forEach((message) => message.remove());
        items.forEach((item) => {
            const element = document.createElement("article");
            element.className = `message message-${item.role}`;
            if (item.role === "assistant" && item.response) {
                element.innerHTML = `<div class="message-meta">Assistant</div>${renderResult(item.response)}`;
            } else {
                element.innerHTML = `<div class="message-meta">You</div><div>${escapeHtml(item.text)}</div>`;
            }
            messages.appendChild(element);
        });
    };

    let conversation = loadMessages();
    renderMessages(conversation);

    const setLoading = (loading) => {
        submitButton.disabled = loading;
        submitLabel.hidden = loading;
        loadingLabel.hidden = !loading;
        form.classList.toggle("loading", loading);
    };

    const showError = (message) => {
        formError.textContent = message;
        formError.hidden = false;
    };

    const validate = (data) => {
        if (!data.question.trim()) return "Please enter a question or preference.";
        if (!data.date) return "Please choose a date.";
        const estimated = data.estimatedKwh === "" ? null : Number(data.estimatedKwh);
        if (estimated !== null && (!Number.isFinite(estimated) || estimated <= 0)) return "Estimated consumption must be greater than zero.";
        if ((data.earliestStart && !data.latestEnd) || (!data.earliestStart && data.latestEnd)) return "Enter both an earliest start and a latest finish.";
        if (data.earliestStart && data.latestEnd) {
            const toMinutes = (time) => {
                const [hours, minutes] = time.split(":").map(Number);
                return hours * 60 + minutes;
            };
            const start = toMinutes(data.earliestStart);
            const end = toMinutes(data.latestEnd);
            if (end <= start || (end - start) < Number(data.duration)) return "The selected time window must be at least as long as the appliance duration.";
        }
        return null;
    };

    const readError = async (response) => {
        let body;
        try { body = await response.json(); } catch { body = null; }
        return body && (body.message || body.error) || `The request failed (${response.status}). Please try again.`;
    };

    form.addEventListener("submit", async (event) => {
        event.preventDefault();
        formError.hidden = true;
        const data = Object.fromEntries(new FormData(form).entries());
        const validationError = validate(data);
        if (validationError) { showError(validationError); return; }
        const question = data.estimatedKwh
            ? `${data.question.trim()} (User estimate: ${data.estimatedKwh} kWh for the complete cycle.)`
            : data.question.trim();
        const request = {
            question,
            date: data.date,
            region: data.region,
            duration: `PT${data.duration}H`,
            earliestStart: data.earliestStart || null,
            latestEnd: data.latestEnd || null,
            machineType: data.machineType
        };
        conversation.push({ role: "user", text: data.question.trim() });
        saveMessages(conversation);
        renderMessages(conversation);
        setLoading(true);
        try {
            const response = await fetch("/api/advice", {
                method: "POST",
                headers: { "Content-Type": "application/json", "Accept": "application/json" },
                body: JSON.stringify(request)
            });
            if (!response.ok) throw new Error(await readError(response));
            const result = await response.json();
            conversation.push({ role: "assistant", response: result });
            saveMessages(conversation);
            renderMessages(conversation);
            form.reset();
            document.querySelector("#date").value = data.date;
        } catch (error) {
            showError(error.message || "The assistant could not complete the request. Please try again.");
        } finally {
            setLoading(false);
        }
    });

    document.querySelector("#clear-conversation").addEventListener("click", () => {
        conversation = [];
        saveMessages(conversation);
        renderMessages(conversation);
    });
})();