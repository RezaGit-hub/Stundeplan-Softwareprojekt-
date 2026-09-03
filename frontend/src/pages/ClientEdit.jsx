import { useEffect, useState } from 'react'
import { useNavigate, useParams } from 'react-router-dom'

export default function ClientEdit() {
	const { id } = useParams()          // undefined on /clients/new
	const navigate = useNavigate()
	const isNew = id === undefined
	const [item, setItem] = useState({ name: '', email: '' })

	useEffect(() => {
		if (!isNew) {
		fetch(`/api/clients/${id}`)
			.then(res => res.json())
			.then(setItem)
		}
	}, [id])

	function handleChange(e) {
		setItem({ ...item, [e.target.name]: e.target.value })
	}

	async function handleSubmit(e) {
		e.preventDefault()
		await fetch(`/api/clients${item.id ? '/' + item.id : ''}`, {
		method: item.id ? 'PUT' : 'POST',
		headers: { 'Content-Type': 'application/json' },
		body: JSON.stringify(item),
		})
		navigate('/clients')
	}

	return (
		<div className="p-8 max-w-md">
		<h2 className="text-xl font-bold mb-4">{isNew ? 'Add Client' : 'Edit Client'}</h2>
		<form onSubmit={handleSubmit} className="space-y-4">
			<div>
			<label className="block text-sm font-medium mb-1">Name</label>
			<input
				name="name"
				value={item.name || ''}
				onChange={handleChange}
				className="w-full border rounded px-3 py-2 focus:outline-none focus:ring-2 focus:ring-blue-500"
			/>
			</div>
			<div>
			<label className="block text-sm font-medium mb-1">Email</label>
			<input
				name="email"
				value={item.email || ''}
				onChange={handleChange}
				className="w-full border rounded px-3 py-2 focus:outline-none focus:ring-2 focus:ring-blue-500"
			/>
			</div>
			<div className="flex gap-2">
			<button type="submit" className="bg-blue-600 text-white px-4 py-2 rounded hover:bg-blue-700">
				Save
			</button>
			<button
				type="button"
				onClick={() => navigate('/clients')}
				className="bg-gray-200 px-4 py-2 rounded hover:bg-gray-300"
			>
				Cancel
			</button>
			</div>
		</form>
		</div>
	)
}