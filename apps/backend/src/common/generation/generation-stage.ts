import { GenerationStageKey } from '@trippin/api-contracts';

/**
 * Called by the generation pipeline as it reaches each real stage, so the client can show
 * what is actually happening. Keys come from the shared contract, which keeps the worker
 * and the planner UI describing the same pipeline.
 */
export type GenerationStageReporter = (key: GenerationStageKey, message: string) => void;
