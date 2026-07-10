import type { ComponentPropsWithRef, ReactNode } from 'react'

interface FieldShellProps {
  id: string
  label: string
  error?: string
  hint?: string
  children: ReactNode
}

function FieldShell({ id, label, error, hint, children }: FieldShellProps) {
  return (
    <div className="flex flex-col gap-1.5">
      <label htmlFor={id} className="text-sm font-medium text-slate-200">
        {label}
      </label>
      {children}
      {error !== undefined ? (
        <p id={`${id}-error`} role="alert" className="text-sm text-red-400">
          {error}
        </p>
      ) : (
        hint !== undefined && <p className="text-sm text-slate-400">{hint}</p>
      )}
    </div>
  )
}

const inputClasses =
  'w-full rounded-xl border border-surface-line bg-surface-raised px-4 py-3 text-slate-100 ' +
  'placeholder:text-slate-500 focus:border-brand-500 focus:outline-none aria-invalid:border-red-400'

type FieldExtras = { label: string; error?: string; hint?: string }

function ariaProps(id: string, error?: string) {
  return {
    'aria-invalid': error !== undefined || undefined,
    'aria-describedby': error !== undefined ? `${id}-error` : undefined,
  }
}

export function TextField({
  label,
  error,
  hint,
  id,
  name,
  ...rest
}: ComponentPropsWithRef<'input'> & FieldExtras) {
  const fieldId = id ?? `field-${name ?? label}`
  return (
    <FieldShell id={fieldId} label={label} error={error} hint={hint}>
      <input
        id={fieldId}
        name={name}
        className={inputClasses}
        {...ariaProps(fieldId, error)}
        {...rest}
      />
    </FieldShell>
  )
}

export function TextAreaField({
  label,
  error,
  hint,
  id,
  name,
  ...rest
}: ComponentPropsWithRef<'textarea'> & FieldExtras) {
  const fieldId = id ?? `field-${name ?? label}`
  return (
    <FieldShell id={fieldId} label={label} error={error} hint={hint}>
      <textarea
        id={fieldId}
        name={name}
        className={`${inputClasses} min-h-28 font-mono text-sm`}
        {...ariaProps(fieldId, error)}
        {...rest}
      />
    </FieldShell>
  )
}

export function SelectField({
  label,
  error,
  hint,
  id,
  name,
  children,
  ...rest
}: ComponentPropsWithRef<'select'> & FieldExtras) {
  const fieldId = id ?? `field-${name ?? label}`
  return (
    <FieldShell id={fieldId} label={label} error={error} hint={hint}>
      <select
        id={fieldId}
        name={name}
        className={inputClasses}
        {...ariaProps(fieldId, error)}
        {...rest}
      >
        {children}
      </select>
    </FieldShell>
  )
}
