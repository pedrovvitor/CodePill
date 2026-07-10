import type { ComponentPropsWithRef } from 'react'

type Variant = 'primary' | 'ghost'

interface ButtonProps extends ComponentPropsWithRef<'button'> {
  variant?: Variant
}

const base =
  'inline-flex min-h-12 items-center justify-center gap-2 rounded-xl px-5 font-medium ' +
  'transition-colors disabled:cursor-not-allowed disabled:opacity-50 focus-visible:outline-2 ' +
  'focus-visible:outline-offset-2 focus-visible:outline-brand-500'

const variants: Record<Variant, string> = {
  primary: 'bg-brand-600 text-white hover:bg-brand-500 active:bg-brand-700',
  ghost: 'border border-surface-line bg-transparent text-slate-200 hover:bg-surface-raised',
}

export function Button({
  variant = 'primary',
  className = '',
  type = 'button',
  ...rest
}: ButtonProps) {
  return <button type={type} className={`${base} ${variants[variant]} ${className}`} {...rest} />
}
