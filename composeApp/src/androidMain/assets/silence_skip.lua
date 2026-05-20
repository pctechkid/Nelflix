local mp = require "mp"

local MAX_SPEED = 50
local NORMAL_SPEED = 1

local skip = false
local startTime = nil
local searchTimer = nil
local lastToggleTime = 0

local opts = {
    quietness = -30,
    duration = 0.5,
    max_search_seconds = 20,
    debounce_seconds = 0.7,
    show_osd = false
}

local silenceTrigger

local function osd(text, duration)
    if opts.show_osd then
        mp.osd_message(text, duration or 2)
    end
end

local function setTime(time)
    if time then
        mp.set_property_number("time-pos", time)
    end
end

local function getTime()
    return mp.get_property_number("time-pos", 0)
end

local function setSpeed(speed)
    mp.set_property_number("speed", speed)
end

local function setPause(state)
    mp.set_property_bool("pause", state)
end

local function setMute(state)
    mp.set_property_bool("mute", state)
end

local function initAudioFilter()
    local af_table = mp.get_property_native("af") or {}

    for _, f in ipairs(af_table) do
        if f.label == "silencedetect" then
            return
        end
    end

    af_table[#af_table + 1] = {
        enabled = false,
        label = "silencedetect",
        name = "lavfi",
        params = {
            graph = "silencedetect=noise=" .. opts.quietness .. "dB:d=" .. opts.duration
        }
    }

    mp.set_property_native("af", af_table)
end

local function initVideoFilter()
    local vf_table = mp.get_property_native("vf") or {}

    for _, f in ipairs(vf_table) do
        if f.label == "blackout" then
            return
        end
    end

    vf_table[#vf_table + 1] = {
        enabled = false,
        label = "blackout",
        name = "lavfi",
        params = {
            graph = "null"
        }
    }

    mp.set_property_native("vf", vf_table)
end

local function setAudioFilter(state)
    local af_table = mp.get_property_native("af") or {}

    for i = #af_table, 1, -1 do
        if af_table[i].label == "silencedetect" then
            af_table[i].enabled = state
            mp.set_property_native("af", af_table)
            return
        end
    end
end

local function setVideoFilter(state)
    local vf_table = mp.get_property_native("vf") or {}

    for i = #vf_table, 1, -1 do
        if vf_table[i].label == "blackout" then
            vf_table[i].enabled = state

            if state then
                vf_table[i].params = {
                    graph = "drawbox=x=0:y=0:w=iw:h=ih:color=black@1:t=fill"
                }
            else
                vf_table[i].params = {
                    graph = "null"
                }
            end

            mp.set_property_native("vf", vf_table)
            return
        end
    end
end

local function stopTimer()
    if searchTimer then
        searchTimer:kill()
        searchTimer = nil
    end
end

local function cleanupSkip()
    setAudioFilter(false)
    setVideoFilter(false)

    if silenceTrigger then
        mp.unobserve_property(silenceTrigger)
    end

    stopTimer()
    setMute(false)
    setSpeed(NORMAL_SPEED)
    skip = false
end

local function stopSkip(reason)
    local backTime = startTime
    cleanupSkip()

    if reason == "cancel" then
        if backTime then
            setTime(backTime)
        end
        osd("Cancelled silence skip", 2)
    elseif reason == "timeout" then
        if backTime then
            setTime(backTime)
        end
        osd("No silence found", 2)
    elseif reason ~= "found" then
        osd("Silence skip stopped", 2)
    end

    startTime = nil
end

local function parseSilenceStart(value)
    if value == nil then
        return nil
    end

    if type(value) == "table" then
        return tonumber(value["lavfi.silence_start"] or value["silence_start"])
    end

    local text = tostring(value)
    return tonumber(
        text:match("lavfi%.silence_start[^%d]*(%d+%.?%d*)")
        or text:match("silence_start[^%d]*(%d+%.?%d*)")
    )
end

local function formatDuration(seconds)
    seconds = math.floor(tonumber(seconds) or 0)

    if seconds < 1 then
        return "less than 1 second"
    end

    local minutes = math.floor(seconds / 60)
    local remainingSeconds = seconds % 60

    if minutes > 0 and remainingSeconds > 0 then
        local minText = minutes == 1 and "1 minute" or tostring(minutes) .. " minutes"
        local secText = remainingSeconds == 1 and "1 second" or tostring(remainingSeconds) .. " seconds"
        return minText .. " " .. secText
    elseif minutes > 0 then
        return minutes == 1 and "1 minute" or tostring(minutes) .. " minutes"
    else
        return seconds == 1 and "1 second" or tostring(seconds) .. " seconds"
    end
end

silenceTrigger = function(name, value)
    if not skip then
        return
    end

    local detectedTime = parseSilenceStart(value)
    if not detectedTime then
        return
    end

    if startTime and detectedTime <= startTime + 0.2 then
        return
    end

    local skippedSeconds = 0
    if startTime then
        skippedSeconds = detectedTime - startTime
    end

    stopSkip("found")
    setTime(detectedTime)
    osd("Skipped " .. formatDuration(skippedSeconds), 3)
end

local function startSkip()
    startTime = getTime()
    skip = true

    initAudioFilter()
    initVideoFilter()
    mp.observe_property("af-metadata/silencedetect", "native", silenceTrigger)

    setAudioFilter(true)
    setVideoFilter(true)
    setPause(false)
    setMute(true)
    setSpeed(MAX_SPEED)

    stopTimer()
    searchTimer = mp.add_timeout(opts.max_search_seconds, function()
        if skip then
            stopSkip("timeout")
        end
    end)

    osd("Searching for silence...", 3)
end

local function toggleSkip()
    local now = mp.get_time()

    if now - lastToggleTime < opts.debounce_seconds then
        return
    end

    lastToggleTime = now

    if skip then
        stopSkip("cancel")
    else
        startSkip()
    end
end

mp.register_event("start-file", function()
    startTime = nil
end)

mp.register_script_message("toggle", toggleSkip)
mp.msg.info("[silence_skip] Loaded successfully with NELFLIX Skip button trigger")
