import { RoleGate } from "@/components/layout/RoleGate";

const ROLES = ["PASSENGER", "DRIVER", "ADMIN"] as const;

export default function AccountLayout({ children }: { children: React.ReactNode }) {
  return <RoleGate roles={ROLES}>{children}</RoleGate>;
}
