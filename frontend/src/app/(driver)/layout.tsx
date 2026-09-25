import { RoleGate } from "@/components/layout/RoleGate";

const ROLES = ["DRIVER"] as const;

export default function DriverLayout({ children }: { children: React.ReactNode }) {
  return <RoleGate roles={ROLES}>{children}</RoleGate>;
}
