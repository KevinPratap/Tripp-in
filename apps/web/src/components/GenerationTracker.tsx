'use client';

/**
 * Live generation tracker.
 *
 * Every step below is a stage the backend actually reports through
 * GET /api/v1/trips/:id/status as currentStepKey. Nothing here is a decorative animation:
 * a step shows as done only once the worker has reported a later stage, and the line uses
 * the progress the worker sent with that stage.
 *
 * The wait runs to two minutes, which is a long time to stare at a list. So the current stage is
 * the biggest thing on the panel, it is written as a sentence a person would say, and each stage
 * that finishes lands with one small mark instead of appearing silently.
 */

export type GenerationStepKey =
  | 'queued'
  | 'forecast'
  | 'geocoding'
  | 'venues'
  | 'planning'
  | 'validation'
  | 'persist';

const STEPS: Array<{ key: GenerationStepKey; label: string }> = [
  { key: 'queued', label: 'Reading your request' },
  { key: 'forecast', label: 'Checking the weather for your dates' },
  { key: 'geocoding', label: 'Finding your destination on the map' },
  { key: 'venues', label: 'Looking up the places and their opening hours' },
  { key: 'planning', label: 'Deciding what to do each day' },
  { key: 'validation', label: 'Checking travel times, opening hours and your budget' },
  { key: 'persist', label: 'Saving your trip' }
];

interface Props {
  currentStepKey?: string | null;
  statusMessage?: string | null;
  progressPercent?: number;
  isGenerating?: boolean;
  errorMsg?: string | null;
  done?: boolean;
  /** Shown as the eyebrow so the wait reads as being about their trip, not about the machine. */
  destination?: string;
}

export default function GenerationTracker({
  currentStepKey,
  statusMessage,
  progressPercent = 0,
  isGenerating = false,
  errorMsg,
  done = false,
  destination
}: Props) {
  const currentIndex = STEPS.findIndex((step) => step.key === currentStepKey);
  const activeIndex = done ? STEPS.length : currentIndex;
  const percent = errorMsg ? 100 : Math.max(0, Math.min(100, progressPercent));
  const city = String(destination || '').split(',')[0].trim();

  const headline = errorMsg
    ? 'That run stopped'
    : done
      ? 'Your trip is ready'
      : isGenerating && currentIndex >= 0
        ? STEPS[currentIndex].label
        : 'Ready when you are';

  return (
    <div className="comic-panel rounded-2xl bg-white p-6 sm:p-8 space-y-5 motion-arrive">
      <div className="flex items-center justify-between gap-3 border-b-2 border-[#18181B] pb-3">
        <span className="text-xs font-black uppercase tracking-widest text-[#E11D48]">
          Building your trip
        </span>
        <span
          className={`text-[12px] font-black uppercase px-2 py-0.5 border-2 border-[#18181B] rounded-xs ${
            errorMsg
              ? 'bg-[#E11D48] text-white'
              : done
                ? 'bg-emerald-100 text-emerald-900'
                : isGenerating
                  ? 'bg-[#E11D48] text-white'
                  : 'bg-[#FAF8F5] text-[#52525B]'
          }`}
        >
          {errorMsg ? 'Stopped' : done ? 'Ready' : isGenerating ? 'Working' : 'Waiting'}
        </span>
      </div>

      {/* The current stage, as the largest thing on the panel. */}
      <div className="space-y-1">
        {city && (
          <span className="block text-[12px] font-black uppercase tracking-widest text-[#52525B]">
            {city}
          </span>
        )}
        <h3 className="font-display font-black text-xl sm:text-2xl leading-tight text-[#18181B]">
          {headline}
        </h3>
      </div>

      {(isGenerating || done || errorMsg) && (
        <div className="space-y-3">
          <div className="w-full bg-[#FAF8F5] border-2 border-[#18181B] rounded-lg h-3 overflow-hidden p-0.5">
            <div
              className={`h-full rounded-xs transition-all duration-500 ${
                errorMsg ? 'bg-[#18181B]' : 'bg-[#E11D48]'
              }`}
              style={{ width: `${percent}%` }}
            />
          </div>
          {statusMessage && !done && !errorMsg && (
            <p className="text-[12px] font-bold text-[#52525B] leading-snug break-words">
              {statusMessage}
            </p>
          )}
        </div>
      )}

      <ol className="space-y-2.5">
        {STEPS.map((step, index) => {
          const isDone = index < activeIndex && !errorMsg;
          const isActive = !done && !errorMsg && index === activeIndex && isGenerating;
          return (
            <li key={step.key} className="flex items-start gap-3">
              <span
                className={`mt-0.5 w-5 h-5 shrink-0 border-2 border-[#18181B] flex items-center justify-center text-[12px] font-black ${
                  isDone
                    ? 'bg-emerald-100 text-emerald-900 motion-mark'
                    : isActive
                      ? 'bg-[#E11D48] text-white'
                      : 'bg-[#FAF8F5] text-[#52525B]'
                }`}
                aria-hidden="true"
              >
                {isDone ? 'OK' : index + 1}
              </span>
              <span
                className={`text-[12px] font-bold leading-snug ${
                  isDone || isActive ? 'text-[#18181B]' : 'text-[#52525B]'
                }`}
              >
                {step.label}
              </span>
            </li>
          );
        })}
      </ol>

      {!isGenerating && !done && !errorMsg && (
        <p className="text-[12px] font-medium text-[#52525B] leading-relaxed">
          Enter a destination and travel window, then run the engine. Each step above is ticked off
          as the engine reaches it, so you are watching real work rather than a timer.
        </p>
      )}

      {errorMsg && (
        <p className="text-[12px] font-bold text-[#52525B] leading-relaxed">
          Nothing was saved for this run. Adjust the request and run it again.
        </p>
      )}
    </div>
  );
}
