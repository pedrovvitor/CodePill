import { useForm, type UseFormSetError } from 'react-hook-form'
import { zodResolver } from '@hookform/resolvers/zod'
import { Link } from 'react-router'
import {
  createPillFormSchema,
  suggestSlug,
  toPillInput,
  type CreatePillFormValues,
} from '../../domain/catalog/pill-input-schema'
import { PILL_TYPES } from '../../domain/catalog/pill'
import { isApiError } from '../../application/ports/pill-api-port'
import { useCreatePill } from '../../application/pills/use-create-pill'
import { RoleGate } from '../components/RoleGate'
import { Button } from '../design-system/Button'
import { SelectField, TextAreaField, TextField } from '../design-system/fields'

/** API field names → form field names (duration is authored in minutes). */
const SERVER_FIELD_MAP: Record<string, keyof CreatePillFormValues> = {
  title: 'title',
  slug: 'slug',
  summary: 'summary',
  type: 'type',
  estimatedDurationSeconds: 'estimatedMinutes',
  content: 'contentJson',
}

function applyServerError(error: unknown, setError: UseFormSetError<CreatePillFormValues>): void {
  if (isApiError(error)) {
    if (error.status === 409) {
      setError('slug', { type: 'server', message: error.problem?.title ?? 'Slug already in use' })
      return
    }
    const fieldErrors = error.problem?.errors
    if (error.status === 400 && fieldErrors !== undefined) {
      for (const [field, message] of Object.entries(fieldErrors)) {
        const formField = SERVER_FIELD_MAP[field]
        if (formField !== undefined) setError(formField, { type: 'server', message })
      }
      return
    }
    setError('root.server', { message: error.problem?.title ?? 'Something went wrong' })
    return
  }
  setError('root.server', { message: 'Something went wrong — please try again' })
}

function CreatePillForm() {
  const createPill = useCreatePill()
  const {
    register,
    handleSubmit,
    setValue,
    setError,
    getFieldState,
    reset,
    formState: { errors },
  } = useForm<CreatePillFormValues>({
    resolver: zodResolver(createPillFormSchema),
    defaultValues: {
      title: '',
      slug: '',
      summary: '',
      type: 'ARTICLE',
      estimatedMinutes: 5,
      contentJson: '{"blocks": []}',
    },
  })

  if (createPill.isSuccess) {
    return (
      <section className="flex flex-col items-center gap-4 py-16 text-center">
        <h2 className="text-2xl font-semibold text-slate-50">Pill created 🎉</h2>
        <p className="text-slate-300">
          “{createPill.data.title}” was saved as a draft. A curator can publish it to the feed.
        </p>
        <Button
          onClick={() => {
            reset()
            createPill.reset()
          }}
        >
          Create another
        </Button>
        <Link to="/" className="text-sm font-medium text-brand-100 underline">
          Back to feed
        </Link>
      </section>
    )
  }

  const onSubmit = handleSubmit((values) =>
    createPill.mutate(toPillInput(values), {
      onError: (error) => applyServerError(error, setError),
    }),
  )

  return (
    <form onSubmit={(event) => void onSubmit(event)} noValidate className="flex flex-col gap-5">
      <h1 className="text-2xl font-semibold text-slate-50">Create a pill</h1>

      {errors.root?.server !== undefined && (
        <p
          role="alert"
          className="rounded-xl border border-red-400/40 bg-red-950/40 px-4 py-3 text-sm text-red-300"
        >
          {errors.root.server.message}
        </p>
      )}

      <TextField
        label="Title"
        placeholder="Virtual Threads in 5 Minutes"
        error={errors.title?.message}
        {...register('title', {
          onChange: (event: React.ChangeEvent<HTMLInputElement>) => {
            if (!getFieldState('slug').isDirty) {
              setValue('slug', suggestSlug(event.target.value))
            }
          },
        })}
      />

      <TextField
        label="Slug"
        placeholder="virtual-threads-in-5-minutes"
        hint="Unique URL identifier — lowercase, digits, hyphens."
        error={errors.slug?.message}
        {...register('slug')}
      />

      <TextAreaField
        label="Summary"
        placeholder="One or two sentences shown in the feed (optional)."
        error={errors.summary?.message}
        {...register('summary')}
      />

      <div className="grid grid-cols-2 gap-4">
        <SelectField label="Type" error={errors.type?.message} {...register('type')}>
          {PILL_TYPES.map((type) => (
            <option key={type} value={type}>
              {type}
            </option>
          ))}
        </SelectField>

        <TextField
          label="Estimated minutes"
          type="number"
          min={1}
          max={1440}
          inputMode="numeric"
          error={errors.estimatedMinutes?.message}
          {...register('estimatedMinutes', { valueAsNumber: true })}
        />
      </div>

      <TextAreaField
        label="Content (JSON)"
        hint="The pill body as a JSON document."
        error={errors.contentJson?.message}
        {...register('contentJson')}
      />

      <Button type="submit" disabled={createPill.isPending}>
        {createPill.isPending ? 'Creating…' : 'Create pill'}
      </Button>
    </form>
  )
}

/** Authoring page — gated to AUTHOR+ in the UI; the API enforces it again. */
export function CreatePillPage() {
  return (
    <RoleGate
      role="AUTHOR"
      fallback={
        <p className="py-16 text-center text-slate-400">
          You need the AUTHOR role to create pills.
        </p>
      }
    >
      <CreatePillForm />
    </RoleGate>
  )
}
