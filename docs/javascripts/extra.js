/*
 * Looma docs: the small interactive pieces (the render walk-through on the home page, the benchmark charts and the
 * request race). Each one is complete without this script; the script adds motion and controls. Material's instant
 * navigation swaps pages without a reload, so everything is set up again on each page through `document$`.
 */
(function () {
  "use strict";

  var cleanups = [];
  var uid = 0;
  var reduceMotion = function () {
    return window.matchMedia && window.matchMedia("(prefers-reduced-motion: reduce)").matches;
  };

  /** Calls `onEnter` the first time the element scrolls into view (at once if observers are unavailable). */
  function whenVisible(element, onEnter, onLeave) {
    if (!("IntersectionObserver" in window)) {
      onEnter();
      return;
    }
    var observer = new IntersectionObserver(function (entries) {
      entries.forEach(function (entry) {
        if (entry.isIntersecting) onEnter();
        else if (onLeave) onLeave();
      });
    }, { threshold: 0.25 });
    observer.observe(element);
    cleanups.push(function () { observer.disconnect(); });
  }

  // ---------------------------------------------------------------- home: how Looma renders a page

  function initWalkthrough(root) {
    var tabs = Array.prototype.slice.call(root.querySelectorAll("[data-step]"));
    var panels = Array.prototype.slice.call(root.querySelectorAll("[data-panel]"));
    var playButton = root.querySelector("[data-action='play']");
    var current = 0;
    var timer = null;
    var paused = reduceMotion();

    root.classList.add("is-interactive");
    var id = "looma-walk-" + (++uid);
    tabs.forEach(function (tab, i) {
      tab.id = id + "-tab-" + i;
      tab.setAttribute("aria-controls", id + "-panel-" + i);
      panels[i].id = id + "-panel-" + i;
      panels[i].setAttribute("aria-labelledby", tab.id);
    });

    function show(index) {
      current = (index + panels.length) % panels.length;
      tabs.forEach(function (tab, i) {
        var active = i === current;
        tab.setAttribute("aria-selected", active ? "true" : "false");
        tab.tabIndex = active ? 0 : -1;
        tab.classList.toggle("is-done", i < current);
      });
      panels.forEach(function (panel, i) {
        panel.hidden = i !== current;
      });
      root.style.setProperty("--walk-progress", String((current + 1) / panels.length));
    }

    function stop() {
      if (timer) window.clearInterval(timer);
      timer = null;
    }

    function start() {
      stop();
      if (paused) return;
      timer = window.setInterval(function () {
        show(current + 1);
        if (current === panels.length - 1) setPaused(true); // play through once, then rest on the result
      }, 4200);
    }

    function setPaused(value) {
      paused = value;
      if (playButton) {
        playButton.setAttribute("aria-pressed", paused ? "false" : "true");
        playButton.textContent = paused ? "Play" : "Pause";
      }
      start();
    }

    tabs.forEach(function (tab, i) {
      tab.addEventListener("click", function () { show(i); setPaused(true); });
      tab.addEventListener("keydown", function (event) {
        if (event.key === "ArrowRight" || event.key === "ArrowLeft") {
          event.preventDefault();
          show(current + (event.key === "ArrowRight" ? 1 : -1));
          tabs[current].focus();
          setPaused(true);
        }
      });
    });
    root.querySelector("[data-action='prev']").addEventListener("click", function () { show(current - 1); setPaused(true); });
    root.querySelector("[data-action='next']").addEventListener("click", function () { show(current + 1); setPaused(true); });
    if (playButton) playButton.addEventListener("click", function () { setPaused(!paused); });

    // Pause while the reader is looking closely, carry on when they leave.
    root.addEventListener("mouseenter", stop);
    root.addEventListener("mouseleave", start);
    root.addEventListener("focusin", stop);

    show(0);
    setPaused(paused);
    whenVisible(root, start, stop);
    cleanups.push(stop);
  }

  // ---------------------------------------------------------------- benchmarks: charts per page type

  function initBenchmark(root) {
    var tabs = Array.prototype.slice.call(root.querySelectorAll("[data-page-tab]"));
    var panels = Array.prototype.slice.call(root.querySelectorAll("[data-page-panel]"));
    root.classList.add("is-interactive");
    var id = "looma-bench-" + (++uid);
    tabs.forEach(function (tab) {
      var name = tab.getAttribute("data-page-tab");
      var panel = root.querySelector("[data-page-panel='" + name + "']");
      tab.id = id + "-tab-" + name;
      tab.setAttribute("aria-controls", id + "-panel-" + name);
      panel.id = id + "-panel-" + name;
      panel.setAttribute("aria-labelledby", tab.id);
    });

    function grow(panel) {
      if (reduceMotion()) return;
      panel.classList.remove("is-grown");
      void panel.offsetWidth; // restart the transition
      panel.classList.add("is-grown");
    }

    function show(name, animate) {
      tabs.forEach(function (tab) {
        var active = tab.getAttribute("data-page-tab") === name;
        tab.setAttribute("aria-selected", active ? "true" : "false");
        tab.tabIndex = active ? 0 : -1;
      });
      panels.forEach(function (panel) {
        var active = panel.getAttribute("data-page-panel") === name;
        panel.hidden = !active;
        if (active && animate) grow(panel);
      });
    }

    tabs.forEach(function (tab, i) {
      tab.addEventListener("click", function () { show(tab.getAttribute("data-page-tab"), true); });
      tab.addEventListener("keydown", function (event) {
        if (event.key !== "ArrowRight" && event.key !== "ArrowLeft") return;
        event.preventDefault();
        var next = tabs[(i + (event.key === "ArrowRight" ? 1 : tabs.length - 1)) % tabs.length];
        next.focus();
        show(next.getAttribute("data-page-tab"), true);
      });
    });

    show(tabs[tabs.length - 1].getAttribute("data-page-tab"), false);
    var grownOnce = false;
    whenVisible(root, function () {
      if (grownOnce) return;
      grownOnce = true;
      panels.forEach(function (panel) { if (!panel.hidden) grow(panel); });
    });
  }

  // ---------------------------------------------------------------- benchmarks: the request race

  function initRace(root) {
    var lanes = Array.prototype.slice.call(root.querySelectorAll("[data-lane]"));
    var replay = root.querySelector("[data-action='replay']");
    root.classList.add("is-interactive");
    if (reduceMotion()) {
      root.classList.add("is-still");
      return;
    }

    // Real CPU time per heavy page, slowed down so one Thymeleaf page takes about seven seconds on screen.
    var scale = 7000 / 58;
    var frame = null;
    var startedAt = 0;
    var running = false;

    // Two Thymeleaf pages, then the race rests on the final count; Replay runs it again.
    var limit = 2 * 58 * scale;

    function render(now) {
      var elapsed = Math.min(now - startedAt, limit);
      var finished = elapsed >= limit;
      lanes.forEach(function (lane) {
        var period = parseFloat(lane.getAttribute("data-ms")) * scale;
        var pages = Math.floor(elapsed / period);
        var progress = finished ? 1 : (elapsed % period) / period;
        lane.style.setProperty("--race-progress", progress.toFixed(4));
        lane.querySelector("[data-count]").textContent = String(pages);
        var steps = lane.querySelectorAll("[data-at]");
        Array.prototype.forEach.call(steps, function (step) {
          step.classList.toggle("is-active", progress >= parseFloat(step.getAttribute("data-at")));
        });
      });
      if (finished) { stop(); return; }
      if (running) frame = window.requestAnimationFrame(render);
    }

    var done = false;

    function start() {
      if (running || done) return;
      running = true;
      startedAt = performance.now();
      frame = window.requestAnimationFrame(render);
    }

    function stop() {
      if (running && performance.now() - startedAt >= limit) done = true;
      running = false;
      if (frame) window.cancelAnimationFrame(frame);
      frame = null;
    }

    if (replay) replay.addEventListener("click", function () { stop(); done = false; start(); });
    whenVisible(root, start, stop);
    cleanups.push(stop);
  }


  // ---------------------------------------------------------------- extending: the pipeline

  function initPipe(root) {
    root.classList.add("is-interactive");
    var replay = root.querySelector("[data-action='replay']");
    if (replay) replay.addEventListener("click", function () {
      // A fresh copy restarts every CSS animation, including the dots drawn by pseudo-elements.
      var fresh = root.cloneNode(true);
      fresh.classList.remove("is-waiting");
      root.replaceWith(fresh);
      initPipe(fresh);
      fresh.classList.remove("is-waiting");
      var button = fresh.querySelector("[data-action='replay']");
      if (button) button.focus();
    });
    if (reduceMotion()) return;
    if (root.dataset.loomaStarted) return;
    root.classList.add("is-waiting"); // paused until the reader gets there
    whenVisible(root, function () {
      root.classList.remove("is-waiting");
      root.dataset.loomaStarted = "true";
    });
  }

  // ---------------------------------------------------------------- Looma syntax highlighting

  /*
   * Pygments has no lexer for Blade-style templates, so HTML blocks are highlighted as plain HTML and Looma's own
   * syntax inside tags (`@class({...})`, `$loop->first`) comes out as errors. This re-highlights blocks that contain
   * Looma syntax: HTML tags and attributes, `{{ }}` / `{!! !!}` echoes, `@directives` with their arguments, `$variables`,
   * `<x-components>` and comments. Line anchors and Material's code annotations are kept as they are.
   */
  var LOOMA_MARKERS = /\{\{|\{!!|@[A-Za-z]+\s*\(|@end[a-z]+|@(csrf|else|empty|show|parent|break|continue|guest|auth|verbatim|once)\b|<x-/;

  function looksLikeLooma(text) {
    return LOOMA_MARKERS.test(text) && !/\sth:[a-z]/.test(text);
  }

  function tokenizeLooma(src) {
    var out = [];
    var i = 0;
    var n = src.length;
    function push(text, cls) { if (text) out.push({ text: text, cls: cls || "" }); }
    function startsWith(s, at) { return src.substr(at, s.length) === s; }
    function readQuoted(at) { // returns end index after the closing quote (or n)
      var q = src[at];
      var j = at + 1;
      while (j < n && src[j] !== q) j++;
      return Math.min(j + 1, n);
    }
    function expression(from, to) { // tokens of a SpEL-ish expression
      var j = from;
      while (j < to) {
        var c = src[j];
        var rest = src.slice(j, to);
        var m;
        if (/\s/.test(c)) { m = /^\s+/.exec(rest); push(m[0]); j += m[0].length; }
        else if (c === "'" || c === '"') { var e = Math.min(readQuoted(j), to); push(src.slice(j, e), "s2"); j = e; }
        else if ((m = /^\$[A-Za-z_]\w*/.exec(rest))) { push(m[0], "nv lm-var"); j += m[0].length; }
        else if ((m = /^@[A-Za-z_]\w*/.exec(rest))) { push(m[0], "nd lm-bean"); j += m[0].length; }
        else if ((m = /^\d+(\.\d+)?[lLdDfF]?/.exec(rest))) { push(m[0], "mi"); j += m[0].length; }
        else if ((m = /^[A-Za-z_]\w*/.exec(rest))) {
          var word = m[0];
          var cls = /^(true|false|null|and|or|not|as|instanceof)$/.test(word) ? "k"
              : /^\s*\(/.test(src.slice(j + word.length, to)) ? "nf" : "n";
          push(word, cls); j += word.length;
        }
        else if ((m = /^(->|\?\.|\?\?|\?:|==|!=|<=|>=|&&|\|\||=>|[-+*\/%<>!=?:])/.exec(rest))) { push(m[0], "o"); j += m[0].length; }
        else { push(c, "p"); j++; }
      }
    }
    function matchParen(at) { // index of the ')' matching the '(' at `at`, skipping strings
      var depth = 0;
      for (var j = at; j < n; j++) {
        var c = src[j];
        if (c === "'" || c === '"') { j = readQuoted(j) - 1; continue; }
        if (c === "(") depth++;
        else if (c === ")" && --depth === 0) return j;
        else if (c === "\n" && depth === 0) return -1;
      }
      return -1;
    }
    function echo(at) { // {{ }} or {!! !!} at `at`; returns the index after it, or -1
      var raw = startsWith("{!!", at);
      var open = raw ? "{!!" : "{{";
      var close = raw ? "!!}" : "}}";
      var end = src.indexOf(close, at + open.length);
      if (end < 0) return -1;
      push(open, "lm-delim");
      expression(at + open.length, end);
      push(close, "lm-delim");
      return end + close.length;
    }
    function directive(at) { // @name or @name(...) at `at`; returns the index after it, or -1
      var m = /^@([A-Za-z]+)/.exec(src.slice(at, at + 40));
      if (!m) return -1;
      if (at > 0 && /[\w.]/.test(src[at - 1])) return -1; // an e-mail address, not a directive
      var j = at + m[0].length;
      push(m[0], "lm-dir");
      var k = j;
      while (k < n && src[k] === " ") k++;
      if (src[k] === "(") {
        var close = matchParen(k);
        if (close > 0) {
          push(src.slice(j, k));
          push("(", "p");
          expression(k + 1, close);
          push(")", "p");
          return close + 1;
        }
      }
      return j;
    }
    function tag(at) { // an HTML tag at `at`; returns the index after it
      var m = /^<\/?/.exec(src.slice(at));
      push(m[0], "p");
      var j = at + m[0].length;
      var name = /^[A-Za-z][\w:.-]*/.exec(src.slice(j));
      if (name) {
        push(name[0], /^x-/.test(name[0]) ? "nt lm-comp" : "nt");
        j += name[0].length;
      }
      while (j < n) {
        var c = src[j];
        if (c === ">") { push(">", "p"); return j + 1; }
        if (c === "/" && src[j + 1] === ">") { push("/>", "p"); return j + 2; }
        if (/\s/.test(c)) { var ws = /^\s+/.exec(src.slice(j))[0]; push(ws); j += ws.length; continue; }
        if (c === "@") { var d = directive(j); if (d > 0) { j = d; continue; } }
        if (c === "{" && (startsWith("{{", j) || startsWith("{!!", j))) { var e = echo(j); if (e > 0) { j = e; continue; } }
        var attr = /^[^\s=>\/"']+/.exec(src.slice(j));
        if (!attr) { push(c); j++; continue; }
        var bound = attr[0][0] === ":";
        push(attr[0], bound ? "na lm-bind" : "na");
        j += attr[0].length;
        if (src[j] === "=") {
          push("=", "o");
          j++;
          if (src[j] === '"' || src[j] === "'") {
            var end = readQuoted(j);
            var q = src[j];
            push(q, "s");
            if (bound) expression(j + 1, end - 1);
            else value(j + 1, end - 1);
            push(src[end - 1] === q ? q : "", "s");
            j = end;
          }
        }
      }
      return j;
    }
    function value(from, to) { // a quoted attribute value: text with {{ }} inside
      var j = from;
      while (j < to) {
        var k = src.indexOf("{{", j);
        if (k < 0 || k >= to) { push(src.slice(j, to), "s"); return; }
        push(src.slice(j, k), "s");
        var end = src.indexOf("}}", k + 2);
        if (end < 0 || end >= to) { push(src.slice(k, to), "s"); return; }
        push("{{", "lm-delim"); expression(k + 2, end); push("}}", "lm-delim");
        j = end + 2;
      }
    }
    while (i < n) {
      var text = src.slice(i);
      var c = src[i];
      var end;
      if (startsWith("{{--", i)) {
        end = src.indexOf("--}}", i + 4); end = end < 0 ? n : end + 4;
        push(src.slice(i, end), "c lm-comment"); i = end;
      } else if (startsWith("<!--", i)) {
        end = src.indexOf("-->", i + 4); end = end < 0 ? n : end + 3;
        push(src.slice(i, end), "c"); i = end;
      } else if (startsWith("@{{", i) || startsWith("@@", i)) {
        push(src.slice(i, i + (src[i + 1] === "@" ? 2 : 1)), "lm-escape"); i += src[i + 1] === "@" ? 2 : 1;
        if (startsWith("{{", i)) { end = src.indexOf("}}", i); end = end < 0 ? n : end + 2; push(src.slice(i, end)); i = end; }
      } else if (c === "{" && (startsWith("{{", i) || startsWith("{!!", i)) && (end = echo(i)) > 0) {
        i = end;
      } else if (c === "@" && (end = directive(i)) > 0) {
        i = end;
      } else if (c === "<" && /^<\/?[A-Za-z!]/.test(text) && !startsWith("<!", i)) {
        i = tag(i);
      } else {
        var m = /^[^<{@]+/.exec(text);
        var chunk = m ? m[0] : c;
        push(chunk); i += chunk.length;
      }
    }
    return out;
  }

  function escapeHtml(s) {
    return s.replace(/&/g, "&amp;").replace(/</g, "&lt;").replace(/>/g, "&gt;");
  }

  function highlightLooma(code) {
    if (code.dataset.loomaHighlighted) return;
    var lines = [];
    var current = null;
    Array.prototype.slice.call(code.childNodes).forEach(function (node) {
      if (node.nodeType === 1 && node.tagName === "A" && /^__codelineno/.test(node.id || "")) {
        current = { anchor: node, nodes: [] };
        lines.push(current);
      } else {
        if (!current) { current = { anchor: null, nodes: [] }; lines.push(current); }
        current.nodes.push(node);
      }
    });
    var source = "";
    lines.forEach(function (line) {
      line.keep = [];
      line.text = "";
      line.nodes.forEach(function (node) {
        var isAnnotation = node.nodeType === 1 && (node.classList.contains("md-annotation") || node.querySelector(".md-annotation"));
        if (isAnnotation) line.keep.push(node);
        else line.text += node.textContent;
      });
      source += line.text;
    });
    if (!code.classList.contains("language-looma") && !looksLikeLooma(source)) return;
    var tokens = tokenizeLooma(source);
    if (!lines.some(function (line) { return line.anchor; })) { // hand-written blocks: no line anchors to keep
      code.innerHTML = tokens.map(function (token) {
        return token.cls ? '<span class="' + token.cls + '">' + escapeHtml(token.text) + "</span>" : escapeHtml(token.text);
      }).join("");
      code.dataset.loomaHighlighted = "true";
      return;
    }
    var html = "";
    var t = 0;
    var offset = 0; // characters already used from tokens[t]
    lines.forEach(function (line) {
      var remaining = line.text.length;
      var body = "";
      var newline = /\n$/.test(line.text);
      if (newline) remaining -= 1;
      while (remaining > 0 && t < tokens.length) {
        var token = tokens[t];
        var available = token.text.length - offset;
        var take = Math.min(available, remaining);
        var piece = token.text.substr(offset, take);
        var nl = piece.indexOf("\n");
        if (nl >= 0) { piece = piece.slice(0, nl); take = nl; } // never carry a line break inside a span
        body += token.cls ? '<span class="' + token.cls + '">' + escapeHtml(piece) + "</span>" : escapeHtml(piece);
        offset += take;
        remaining -= take;
        if (offset >= token.text.length) { t++; offset = 0; }
        if (take === 0) break;
      }
      if (newline) { // step over the line break in the token stream
        if (t < tokens.length && tokens[t].text[offset] === "\n") { offset++; if (offset >= tokens[t].text.length) { t++; offset = 0; } }
      }
      line.html = body;
      line.newline = newline;
    });
    var fragment = document.createDocumentFragment();
    lines.forEach(function (line) {
      if (line.anchor) fragment.appendChild(line.anchor);
      var holder = document.createElement("span");
      holder.innerHTML = line.html;
      while (holder.firstChild) fragment.appendChild(holder.firstChild);
      line.keep.forEach(function (node) { fragment.appendChild(node); });
      if (line.newline) fragment.appendChild(document.createTextNode("\n"));
    });
    code.textContent = "";
    code.appendChild(fragment);
    code.dataset.loomaHighlighted = "true";
    code.closest(".highlight") && code.closest(".highlight").classList.add("lm-looma");
  }

  function highlightAll() {
    document.querySelectorAll(".language-html.highlight code, .highlight.language-html code, code.language-looma")
        .forEach(function (code) {
          try { highlightLooma(code); } catch (e) { /* leave the block as Pygments rendered it */ }
        });
  }

  // ---------------------------------------------------------------- wiring

  function setup() {
    cleanups.splice(0).forEach(function (cleanup) { cleanup(); });
    highlightAll();
    document.querySelectorAll("[data-looma-walkthrough]").forEach(initWalkthrough);
    document.querySelectorAll("[data-looma-benchmark]").forEach(initBenchmark);
    document.querySelectorAll("[data-looma-race]").forEach(initRace);
    document.querySelectorAll("[data-looma-pipe]").forEach(initPipe);
  }

  if (window.document$ && typeof window.document$.subscribe === "function") {
    window.document$.subscribe(setup);
  } else if (document.readyState === "loading") {
    document.addEventListener("DOMContentLoaded", setup);
  } else {
    setup();
  }
})();
