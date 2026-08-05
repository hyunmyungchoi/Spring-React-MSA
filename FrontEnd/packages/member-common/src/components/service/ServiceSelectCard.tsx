import { Link } from "react-router-dom";

type ServiceSelectCardProps = {
    to: string;
    label: string;
    title: string;
};

// Renders a service navigation tile.
function ServiceSelectCard({ to, label, title }: ServiceSelectCardProps) {
    return (
        <Link className="service-tile service-link" to={to}>
            <span>{label}</span>
            <strong>{title}</strong>
        </Link>
    );
}

export default ServiceSelectCard;
