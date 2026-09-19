'use client';

/**
 * Live generation tracker.
 *
 * Every step below is a stage the backend actually reports through
 * GET /api/v1/trips/:id/status as currentStepKey. Nothing here is a decorative animation:
 * a step shows as done only once the worker has reported a later stage, and the bar uses
 * the progress the worker sent with that stage.
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
  { key: 'queued', label: 'Reading the request' },
  { key: 'forecast', label: 'Checking the weather window' },
  { key: 'geocoding', label: 'Geocoding the destination' },
  { key: 'venues', label: 'Resolving OSM venues and opening hours' },
  { key: 'planning', label: 'Scheduling the days' },
  { key: 'validation', label: 'Computing OSRM transit, checking hours, pace and budget' },
  { key: 'persist', label: 'Saving the verified itinerary' }
];

interface Props {
  currentStepKey?: string | null;
  statusMessage?: string | null;
  progressPercent?: number;
  isGenerating?: boolean;
  errorMsg?: string | null;
  done?: boolean;
}

export default function GenerationTracker({
  currentStepKey,
  statusMessage,
  progressPercent = 0,
  isGenerating = false,
  errorMsg,
  done = false
}: Props) {
  const currentIndex = STEPS.findIndex((step) => step.key === currentStepKey);
  const activeIndex = done ? STEPS.length : currentIndex;
  const percent = errorMsg ? 100 : Math.max(0, Math.min(100, progressPercent));

  return (
    <div className="comic-panel rounded-2xl bg-white p-6 sm:p-8 space-y-5">
      <div className="flex items-center justify-between gap-3 border-b-2 border-[#18181B] pb-3">
        <span className="text-xs font-black uppercase tracking-widest text-[#E11D48]">
          ROUTE ASSEMBLY
        </span>
        <span
          className={`text-[10px] font-black uppercase px-2 py-0.5 border-2 border-[#18181B] rounded-xs ${
            errorMsg
              ? 'bg-[#E11D48] text-white'
              : done
                ? 'bg-emerald-100 text-emerald-900'
                : isGenerating
                  ? 'bg-[#E11D48] text-white'
                  : 'bg-[#FAF8F5] text-[#52525B]'
          }`}
        >
          {errorMsg ? 'HALTED' : done ? 'VERIFIED' : isGenerating ? 'RUNNING' : 'IDLE'}
        </span>
      </div>

      <ol className="space-y-2.5">
        {STEPS.map((step, index) => {
          const isDone = index < activeIndex && !errorMsg;
          const isActive = !done && !errorMsg && index === activeIndex && isGenerating;
          return (
            <li key={step.key} className="flex items-start gap-3">
              <span
                className={`mt-0.5 w-5 h-5 shrink-0 border-2 border-[#18181B] flex items-center justify-center text-[10px] font-black ${
                  isDone
                    ? 'bg-emerald-100 text-emerald-900'
                    : isActive
                      ? 'bg-[#E11D48] text-white'
                      : 'bg-[#FAF8F5] text-[#52525B]'
                }`}
                aria-hidden="true"
              >
                {isDone ? 'OK' : index + 1}
              </span>
              <span
                className={`text-xs font-bold leading-snug ${
                  isDone || isActive ? 'text-[#18181B]' : 'text-[#52525B]'
                }`}
              >
                {step.label}
                {isActive && (
                  <span className="ml-2 text-[10px] font-black uppercase text-[#E11D48]">
                    in progress
                  </span>
                )}
              </span>
            </li>
          );
        })}
      </ol>

      {(isGenerating || done || errorMsg) && (
        <div className="space-y-3 pt-1">
          <div className="w-full bg-[#FAF8F5] border-2 border-[#18181B] rounded-lg h-4 overflow-hidden p-0.5">
            <div
              className={`h-full rounded-xs transition-all duration-500 ${
                errorMsg ? 'bg-[#18181B]' : 'bg-[#E11D48]'
              }`}
              style={{ width: `${percent}%` }}
            />
          </div>
          <p className="text-xs font-bold text-[#18181B] leading-snug break-words">
            {errorMsg || statusMessage || 'Working...'}
          </p>
        </div>
      )}

      {!isGenerating && !done && !errorMsg && (
        <p className="text-xs font-medium text-[#52525B] leading-relaxed">
          Enter a destination and travel window on the left, then run the engine. Each step
          above lights up as the engine reaches it.
        </p>
      )}

      {errorMsg && (
        <p className="text-[11px] font-bold text-[#52525B] leading-relaxed">
          Nothing was saved for this run. Adjust the request and run it again.
        </p>
      )}
    </div>
  );
}
