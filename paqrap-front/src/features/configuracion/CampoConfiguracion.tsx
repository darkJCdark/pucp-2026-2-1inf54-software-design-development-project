import type { ReactNode } from "react";

import { Label } from "@/components/ui/label";

interface CampoConfigProps {
  label: string;
  children: ReactNode;
}

export function CampoConfiguracion({
  label,
  children,
}: CampoConfigProps) {
  return (
    <div>
      <Label className="mb-2 block text-xs uppercase text-slate-400">
        {label}
      </Label>

      {children}
    </div>
  );
}